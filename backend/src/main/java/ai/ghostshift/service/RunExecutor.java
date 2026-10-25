package ai.ghostshift.service;

import ai.ghostshift.ai.GhostBrainService;
import ai.ghostshift.ai.OllamaClient;
import ai.ghostshift.api.ApiException;
import ai.ghostshift.config.AppConfig;
import ai.ghostshift.domain.ActionExecution;
import ai.ghostshift.domain.Approval;
import ai.ghostshift.domain.Category;
import ai.ghostshift.domain.Classification;
import ai.ghostshift.domain.ErrorGroup;
import ai.ghostshift.domain.LogAnalysis;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.domain.ReportView;
import ai.ghostshift.domain.Statuses;
import ai.ghostshift.domain.WorkflowDefinition;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.domain.WorkflowStep;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.PathGuard;
import ai.ghostshift.util.Text;
import ai.ghostshift.workflow.ContextClassification;
import ai.ghostshift.workflow.ContextFile;
import ai.ghostshift.workflow.ContextMove;
import ai.ghostshift.workflow.ContextMoveResult;
import ai.ghostshift.workflow.WorkflowContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Closed, step-by-step workflow engine. Every action comes from the static registry.
 * Approval gates are enforced here rather than trusted to the UI, and dry runs never
 * touch user files or save reports.
 */
@Singleton
public class RunExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(RunExecutor.class);
    private static final Set<String> TERMINAL_ACTIONS = Set.of("SUCCEEDED", "FAILED", "SKIPPED", "CANCELLED");
    private static final Set<String> TERMINAL_RUNS = Set.of("COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".log", ".txt", ".out", ".err", ".trace", ".json",
            ".csv", ".tsv", ".md", ".yaml", ".yml", ".conf", ".properties");

    private final RunRepository runs;
    private final WorkflowService workflows;
    private final DirectoryService directories;
    private final FileScanner scanner;
    private final ClassificationService classifications;
    private final SafeFileOperations fileOperations;
    private final PathGuard pathGuard;
    private final LogAnalyzer logAnalyzer;
    private final GhostBrainService brain;
    private final ReportService reports;
    private final SettingsService settings;
    private final ActivityService activity;
    private final AppConfig config;
    private final Json json;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public RunExecutor(RunRepository runs, WorkflowService workflows, DirectoryService directories,
                       FileScanner scanner, ClassificationService classifications,
                       SafeFileOperations fileOperations, PathGuard pathGuard, LogAnalyzer logAnalyzer,
                       GhostBrainService brain, ReportService reports, SettingsService settings,
                       ActivityService activity, AppConfig config, Json json) {
        this.runs = runs;
        this.workflows = workflows;
        this.directories = directories;
        this.scanner = scanner;
        this.classifications = classifications;
        this.fileOperations = fileOperations;
        this.pathGuard = pathGuard;
        this.logAnalyzer = logAnalyzer;
        this.brain = brain;
        this.reports = reports;
        this.settings = settings;
        this.activity = activity;
        this.config = config;
        this.json = json;
    }

    /** Runs (or resumes) one workflow until completion, cancellation, or an approval pause. */
    public void execute(String runId) {
        Object lock = locks.computeIfAbsent(runId, key -> new Object());
        try {
            synchronized (lock) {
            RunRepository.RunRow row = runs.getRow(runId);
            if (TERMINAL_RUNS.contains(row.status())) {
                return;
            }
            WorkflowRun run = runs.get(runId);
            WorkflowDefinition definition = workflows.definitionForRun(run);
            WorkflowContext context = loadContext(runId);
            if (runs.isCancelRequested(runId)) {
                cancel(runId, run, "Cancelled before the run started.");
                return;
            }
            if (row.status().equals(Statuses.RunStatus.QUEUED.name())) {
                runs.markStarted(runId);
                runs.addEvent(runId, "INFO", "Workflow run started"
                        + (run.dryRun() ? " in dry-run mode." : "."), Map.of("dryRun", run.dryRun()));
            }
            Map<String, WorkflowStep> steps = new LinkedHashMap<>();
            for (WorkflowStep step : definition.steps() == null ? List.<WorkflowStep>of() : definition.steps()) {
                if (step != null && step.id() != null) {
                    steps.put(step.id(), step);
                }
            }
            List<ActionExecution> actions = runs.actions(runId);
            for (ActionExecution action : actions) {
                if (runs.isCancelRequested(runId)) {
                    cancel(runId, run, "Cancelled by the user. No further actions were performed.");
                    return;
                }
                if (TERMINAL_ACTIONS.contains(action.status())) {
                    continue;
                }
                WorkflowStep step = steps.get(action.stepId());
                if (step == null) {
                    failAction(action, "The workflow definition no longer contains this step.");
                    continue;
                }
                if (!step.enabled()) {
                    runs.updateAction(action.id(), "SKIPPED", Map.of("reason", "Step disabled."),
                            null, null, Text.now());
                    continue;
                }
                if (action.status().equals(Statuses.StepStatus.WAITING_FOR_APPROVAL.name())) {
                    // Approval is still pending; a resume must resolve it first.
                    return;
                }
                runs.markRunning(runId, action.stepIndex());
                runs.setCurrentStep(runId, action.stepIndex());

                if (action.actionType().equals("REQUEST_USER_APPROVAL")) {
                    if (run.dryRun()) {
                        context = context.withExplicitApproval(true);
                        runs.saveContext(runId, json.write(context));
                        runs.updateAction(action.id(), "SUCCEEDED",
                                Map.of("dryRun", true, "approvalSkipped", "Dry runs do not request approvals."),
                                null, Text.now(), Text.now());
                        runs.addEvent(runId, "INFO", "Dry run skipped the human approval pause.", Map.of());
                        continue;
                    }
                    Approval existing = runs.approvalForAction(runId, action.id());
                    if (existing != null && existing.status().equals("APPROVED")) {
                        context = context.withExplicitApproval(true);
                        runs.saveContext(runId, json.write(context));
                        runs.updateAction(action.id(), "SUCCEEDED", Map.of("approved", true),
                                null, Text.now(), Text.now());
                        continue;
                    }
                    String title = stringParameter(step.parameters(), "title", "Approve the next workflow step");
                    String explanation = stringParameter(step.parameters(), "explanation",
                            "Continue this workflow after reviewing what it will do.");
                    pauseForApproval(run, action, context, "EXPLICIT", title, explanation, "LOW");
                    return;
                }

                if (action.actionType().equals("ANALYZE_LOG") && !run.dryRun()
                        && settings.approvals().logAnalysisRequiresApproval()) {
                    Approval existing = runs.approvalForAction(runId, action.id());
                    if (existing != null && existing.status().equals("PENDING")) {
                        runs.markActionWaiting(action.id());
                        runs.markWaiting(runId, action.stepIndex());
                        return;
                    }
                    if (existing == null || !existing.status().equals("APPROVED")) {
                        String explanation = "GhostShift will analyze " + logTarget(context)
                                + " and save an incident report. Deterministic signatures are extracted first; "
                                + "the configured local model may interpret the evidence if AI is enabled for this step. "
                                + "No file is changed by this step.";
                        pauseForApproval(run, action, context, "LOG_ANALYSIS",
                                "Approve workflow log analysis", explanation, "LOW");
                        return;
                    }
                }

                if (action.actionType().equals("MOVE_FILE") && !run.dryRun()
                        && settings.approvals().workflowMovesRequireApproval()
                        && !context.explicitApprovalGranted()) {
                    Approval existing = runs.approvalForAction(runId, action.id());
                    if (existing != null && existing.status().equals("PENDING")) {
                        runs.markActionWaiting(action.id());
                        runs.markWaiting(runId, action.stepIndex());
                        return;
                    }
                    if (existing != null && existing.status().equals("APPROVED")) {
                        context = context.withExplicitApproval(true);
                        runs.saveContext(runId, json.write(context));
                    } else {
                        int limit = integerParameter(step.parameters(), "maxFiles", 500);
                        int count = Math.min(context.proposedMoves().size(), limit);
                        String explanation = count + " file(s) are ready to move into GhostShift Organizer. "
                                + "GhostShift will not overwrite, delete, or move anything outside the registered folder. "
                                + "The source files are re-checked for changes immediately before each move.";
                        List<String> examples = context.proposedMoves().stream().limit(6)
                                .map(move -> move.sourceRelative() + " → " + move.destinationRelative())
                                .toList();
                        if (!examples.isEmpty()) {
                            explanation += " Examples: " + String.join("; ", examples)
                                    + (context.proposedMoves().size() > examples.size() ? "; …" : "");
                        }
                        pauseForApproval(run, action, context, "FILE_MOVE",
                                "Approve " + count + " file move(s)", explanation, "MODERATE");
                        return;
                    }
                }

                runs.markActionStarted(action.id());
                String startedAt = Text.now();
                try {
                    StepOutcome outcome = perform(run, action, step, context);
                    context = outcome.context();
                    runs.saveContext(runId, json.write(context));
                    if (outcome.failed()) {
                        runs.updateAction(action.id(), "FAILED", outcome.result(), outcome.error(),
                                startedAt, Text.now());
                        runs.addEvent(runId, "ERROR", step.id() + " completed with errors: " + outcome.error(),
                                Map.of("stepId", step.id(), "actionType", action.actionType()));
                        if (isStop(definition)) {
                            failRun(run, definition, action, step, outcome.error());
                            return;
                        }
                    } else {
                        runs.updateAction(action.id(), "SUCCEEDED", outcome.result(), null,
                                startedAt, Text.now());
                        runs.addEvent(runId, "INFO", step.id() + " succeeded.",
                                Map.of("stepId", step.id(), "actionType", action.actionType()));
                    }
                } catch (RuntimeException e) {
                    String message = safeMessage(e);
                    runs.updateAction(action.id(), "FAILED", Map.of(), message, startedAt, Text.now());
                    runs.addEvent(runId, "ERROR", step.id() + " failed: " + message,
                            Map.of("stepId", step.id(), "actionType", action.actionType()));
                    LOG.info("Workflow {} step {} failed: {}", runId, step.id(), message);
                    if (isStop(definition)) {
                        failRun(run, definition, action, step, message);
                        return;
                    }
                }
            }
            finish(run, definition);
            }
        } finally {
            locks.remove(runId);
        }
    }

    public WorkflowContext loadContext(String runId) {
        RunRepository.RunRow row = runs.getRow(runId);
        String raw = runs.contextJson(runId);
        if (raw != null && !raw.isBlank() && !raw.equals("{}")) {
            try {
                WorkflowContext context = json.read(raw, WorkflowContext.class);
                if (context != null) {
                    return context;
                }
            } catch (RuntimeException e) {
                LOG.warn("Stored workflow context for run {} was unreadable; rebuilding metadata only.", runId);
            }
        }
        Long directoryId = longValue(row.inputs().get("directoryId"));
        String directoryName = null;
        if (directoryId != null) {
            try {
                directoryName = directories.get(directoryId).displayName();
            } catch (RuntimeException ignored) {
                directoryName = null;
            }
        }
        return WorkflowContext.empty(directoryId, directoryName);
    }

    private StepOutcome perform(WorkflowRun run, ActionExecution action, WorkflowStep step,
                                WorkflowContext context) {
        String type = action.actionType().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "LIST_FILES" -> listFiles(run, step, context);
            case "FILTER_FILES" -> filterFiles(step, context);
            case "CLASSIFY_FILES" -> classifyFiles(run, step, context);
            case "PROPOSE_DESTINATION" -> proposeDestinations(run, context);
            case "PREVIEW_FILE_MOVE" -> previewMoves(run, context);
            case "MOVE_FILE" -> moveFiles(run, step, context);
            case "HASH_FILE" -> hashFiles(run, context);
            case "READ_APPROVED_TEXT" -> readText(run, step, context);
            case "ANALYZE_LOG" -> analyzeLog(run, step, context);
            case "GENERATE_REPORT" -> generateReport(run, step, context);
            case "REQUEST_USER_APPROVAL" -> throw ApiException.conflict(
                    "The approval step must be handled by the engine.");
            default -> throw ApiException.badRequest("Unsupported workflow action: " + type);
        };
    }

    private StepOutcome listFiles(WorkflowRun run, WorkflowStep step, WorkflowContext context) {
        RegisteredDirectory directory = requireDirectory(context, run, false);
        boolean recursive = booleanParameter(step.parameters(), "recursive", false);
        int maxFiles = integerParameter(step.parameters(), "maxFiles", config.maxScanFiles());
        FileScanner.Scan scan = scanner.scan(directory, recursive, maxFiles);
        if (scan.entries().isEmpty()) {
            throw ApiException.unprocessable("No files were found in the registered folder.");
        }
        List<ContextFile> files = scan.entries().stream()
                .map(file -> new ContextFile(file.id(), file.relativePath(), file.name(),
                        file.extension(), file.sizeBytes(), file.modifiedAt()))
                .toList();
        WorkflowContext updated = context.withDirectory(directory.id(), directory.displayName())
                .withFiles(files);
        if (scan.truncated()) {
            updated = updated.withWarning("The scan limit of " + maxFiles
                    + " file(s) was reached; only the first files were included.");
        }
        directories.markScanned(directory.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("directoryId", directory.id());
        result.put("directory", directory.displayName());
        result.put("files", files.size());
        result.put("recursive", recursive);
        result.put("truncated", scan.truncated());
        result.put("skippedSymlinks", scan.skippedSymlinks());
        return StepOutcome.ok(updated, result);
    }

    private StepOutcome filterFiles(WorkflowStep step, WorkflowContext context) {
        if (context.files().isEmpty()) {
            throw ApiException.unprocessable("There are no files to filter. Add LIST_FILES first.");
        }
        List<String> extensions = stringList(step.parameters().get("extensions")).stream()
                .map(value -> value.startsWith(".") ? value.toLowerCase(Locale.ROOT) : "." + value.toLowerCase(Locale.ROOT))
                .toList();
        String nameContains = stringParameter(step.parameters(), "nameContains", "").toLowerCase(Locale.ROOT);
        List<ContextFile> kept = context.files().stream()
                .filter(file -> extensions.isEmpty()
                        || extensions.contains(file.extension() == null ? "" : file.extension().toLowerCase(Locale.ROOT)))
                .filter(file -> nameContains.isBlank()
                        || file.name().toLowerCase(Locale.ROOT).contains(nameContains))
                .toList();
        if (kept.isEmpty()) {
            throw ApiException.unprocessable("No files matched that filter. Nothing was changed.");
        }
        WorkflowContext updated = context.withSelected(kept.stream().map(ContextFile::id).toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selected", kept.size());
        result.put("removed", context.files().size() - kept.size());
        result.put("extensions", extensions);
        result.put("nameContains", nameContains);
        return StepOutcome.ok(updated, result);
    }

    private StepOutcome classifyFiles(WorkflowRun run, WorkflowStep step, WorkflowContext context) {
        List<ContextFile> selected = context.selectedFiles();
        if (selected.isEmpty()) {
            throw ApiException.unprocessable("There are no selected files to classify.");
        }
        RegisteredDirectory directory = requireDirectory(context, run, false);
        Path root = directories.root(directory);
        boolean useAi = booleanParameter(step.parameters(), "useAi", false);
        List<FileScanner.ScannedFile> entries = selected.stream()
                .map(file -> new FileScanner.ScannedFile(file.id(), root.resolve(file.relativePath()),
                        file.relativePath(), file.name(), file.extension(), file.sizeBytes(), file.modifiedAt()))
                .toList();
        ClassificationService.Outcome outcome = classifications.classify(entries, useAi);
        Map<String, ContextClassification> map = new LinkedHashMap<>(context.classifications());
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        for (ContextFile file : selected) {
            Classification classification = outcome.get(file.id());
            map.put(file.id(), new ContextClassification(classification.category(),
                    classification.confidence(), classification.rationale(), classification.source()));
            sourceCounts.merge(classification.source(), 1, Integer::sum);
        }
        WorkflowContext updated = context.withClassifications(map);
        if (outcome.warning() != null) {
            updated = updated.withWarning(outcome.warning());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classified", selected.size());
        result.put("useAi", useAi);
        result.put("aiAttempted", outcome.aiAttempted());
        result.put("aiApplied", outcome.aiApplied());
        result.put("sources", sourceCounts);
        if (outcome.warning() != null) {
            result.put("warning", outcome.warning());
        }
        return StepOutcome.ok(updated, result);
    }

    private StepOutcome proposeDestinations(WorkflowRun run, WorkflowContext context) {
        List<ContextFile> selected = context.selectedFiles();
        if (selected.isEmpty()) {
            throw ApiException.unprocessable("There are no selected files to propose destinations for.");
        }
        RegisteredDirectory directory = requireDirectory(context, run, false);
        Path root = directories.root(directory);
        List<ContextMove> moves = new ArrayList<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        Set<String> destinations = new LinkedHashSet<>();
        int conflicts = 0;
        for (ContextFile file : selected) {
            ContextClassification classification = context.classifications().get(file.id());
            Category category = classification == null ? Category.OTHER : classification.category();
            categoryCounts.merge(category.name(), 1, Integer::sum);
            String fileName = Text.safeFileName(file.name());
            String destination = FileScanner.ORGANIZER_FOLDER + "/" + settings.folderFor(category) + "/" + fileName;
            boolean conflict = destinations.contains(destination)
                    || Files.exists(pathGuard.resolveForWrite(root, destination), LinkOption.NOFOLLOW_LINKS);
            if (conflict) {
                conflicts++;
            }
            moves.add(new ContextMove(file.relativePath(), destination, file.name(), file.sizeBytes(),
                    file.modifiedAt(), category, conflict));
            destinations.add(destination);
        }
        WorkflowContext updated = context.withMoves(moves);
        if (conflicts > 0) {
            updated = updated.withWarning(conflicts
                    + " destination conflict(s) will be renamed safely; nothing will be overwritten.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moves", moves.size());
        result.put("conflicts", conflicts);
        result.put("categories", categoryCounts);
        result.put("preview", moves.stream().limit(20)
                .map(move -> Map.of("source", move.sourceRelative(), "destination", move.destinationRelative()))
                .toList());
        return StepOutcome.ok(updated, result);
    }

    private StepOutcome previewMoves(WorkflowRun run, WorkflowContext context) {
        if (context.proposedMoves().isEmpty()) {
            throw ApiException.unprocessable("There are no proposed moves to preview.");
        }
        RegisteredDirectory directory = requireDirectory(context, run, true);
        Path root = directories.root(directory);
        List<ContextMove> checked = new ArrayList<>();
        List<Map<String, Object>> preview = new ArrayList<>();
        int conflicts = 0;
        int missingSources = 0;
        for (ContextMove move : context.proposedMoves()) {
            boolean missing = false;
            boolean conflict = move.conflict();
            try {
                Path source = pathGuard.resolveExistingFile(root, move.sourceRelative());
                missing = !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS);
            } catch (RuntimeException e) {
                missing = true;
            }
            try {
                Path desired = pathGuard.resolveForWrite(root, move.destinationRelative());
                if (Files.exists(desired, LinkOption.NOFOLLOW_LINKS)) {
                    conflict = true;
                }
            } catch (RuntimeException e) {
                conflict = true;
            }
            if (missing) {
                missingSources++;
                conflict = true;
            }
            if (conflict) {
                conflicts++;
            }
            checked.add(new ContextMove(move.sourceRelative(), move.destinationRelative(), move.fileName(),
                    move.sizeBytes(), move.modifiedAt(), move.category(), conflict));
            if (preview.size() < 50) {
                preview.add(Map.of("source", move.sourceRelative(), "destination", move.destinationRelative(),
                        "sizeBytes", move.sizeBytes(), "conflict", conflict, "missingSource", missing));
            }
        }
        if (missingSources == context.proposedMoves().size()) {
            throw ApiException.conflict("None of the proposed source files are still available. Rescan before moving.");
        }
        WorkflowContext updated = context.withMoves(checked);
        if (conflicts > 0 || missingSources > 0) {
            updated = updated.withWarning(conflicts + " preview issue(s): "
                    + missingSources + " missing source(s). Conflicting destinations will be renamed safely.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("previewed", checked.size());
        result.put("conflicts", conflicts);
        result.put("missingSources", missingSources);
        result.put("files", preview);
        return StepOutcome.ok(updated, result);
    }

    private StepOutcome moveFiles(WorkflowRun run, WorkflowStep step, WorkflowContext context) {
        if (context.proposedMoves().isEmpty()) {
            throw ApiException.unprocessable("There are no proposed moves to execute.");
        }
        int limit = Math.min(integerParameter(step.parameters(), "maxFiles", 500), context.proposedMoves().size());
        if (run.dryRun()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dryRun", true);
            result.put("wouldMove", limit);
            result.put("files", context.proposedMoves().stream().limit(20)
                    .map(move -> Map.of("source", move.sourceRelative(), "destination", move.destinationRelative()))
                    .toList());
            result.put("notice", "Dry run: no files were moved and no recovery records were written.");
            return StepOutcome.ok(context, result);
        }
        RegisteredDirectory directory = requireDirectory(context, run, true);
        Path root = directories.root(directory);
        List<ContextMoveResult> results = new ArrayList<>(context.moveResults());
        int moved = 0;
        int failed = 0;
        String firstError = null;
        int attempted = 0;
        for (ContextMove move : context.proposedMoves()) {
            if (attempted >= limit) {
                break;
            }
            attempted++;
            if (runs.isCancelRequested(run.id())) {
                break;
            }
            try {
                SafeFileOperations.MoveOutcome outcome = fileOperations.moveVerified(root,
                        move.sourceRelative(), move.destinationRelative(), move.sizeBytes(), move.modifiedAt());
                String operationId = UUID.randomUUID().toString();
                runs.insertFileOperation(operationId, run.id(), null,
                        root.resolve(move.sourceRelative()).toAbsolutePath().toString(),
                        outcome.absoluteDestination(), json.write(outcome.recovery()));
                results.add(new ContextMoveResult(move.sourceRelative(), outcome.actualRelative(), true, null));
                moved++;
            } catch (RuntimeException e) {
                String message = safeMessage(e);
                if (firstError == null) {
                    firstError = message;
                }
                failed++;
                results.add(new ContextMoveResult(move.sourceRelative(), move.destinationRelative(), false, message));
                runs.addEvent(run.id(), "WARN", "File was not moved: " + move.sourceRelative() + ". " + message, Map.of());
            }
        }
        WorkflowContext updated = context.withMoveResults(results).withExplicitApproval(false);
        if (failed > 0) {
            updated = updated.withWarning(failed + " file(s) could not be moved and were left untouched. "
                    + moved + " file(s) moved safely.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moved", moved);
        result.put("failed", failed);
        result.put("attempted", attempted);
        result.put("skipped", context.proposedMoves().size() - attempted);
        result.put("dryRun", false);
        if (failed > 0) {
            result.put("firstError", firstError);
        }
        activity.record("ORGANIZATION", "Workflow moved files",
                run.workflowName() + " · " + moved + " moved, " + failed + " failed", run.id());
        return new StepOutcome(updated, result, failed > 0,
                failed > 0 ? failed + " file(s) could not be moved: " + firstError : null);
    }

    private StepOutcome hashFiles(WorkflowRun run, WorkflowContext context) {
        List<ContextFile> selected = context.selectedFiles();
        if (selected.isEmpty()) {
            throw ApiException.unprocessable("There are no selected files to hash.");
        }
        RegisteredDirectory directory = requireDirectory(context, run, false);
        Path root = directories.root(directory);
        Map<String, List<Map<String, Object>>> byHash = new LinkedHashMap<>();
        int hashed = 0;
        int skippedLarge = 0;
        int unreadable = 0;
        for (ContextFile file : selected) {
            try {
                Path path = pathGuard.resolveExistingFile(root, file.relativePath());
                long size = Files.size(path);
                if (size > 100L * 1024 * 1024) {
                    skippedLarge++;
                    continue;
                }
                String hash = SafeFileOperations.hashFile(path);
                byHash.computeIfAbsent(hash, key -> new ArrayList<>()).add(Map.of(
                        "relativePath", file.relativePath(),
                        "name", file.name(),
                        "sizeBytes", size));
                hashed++;
            } catch (IOException | RuntimeException e) {
                unreadable++;
            }
        }
        List<Map<String, Object>> groups = byHash.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> {
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("sha256", entry.getKey());
                    group.put("files", entry.getValue());
                    group.put("count", entry.getValue().size());
                    group.put("sizeBytes", entry.getValue().getFirst().get("sizeBytes"));
                    return group;
                })
                .sorted((a, b) -> Integer.compare((int) b.get("count"), (int) a.get("count")))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hashed", hashed);
        result.put("skippedLarge", skippedLarge);
        result.put("unreadable", unreadable);
        result.put("duplicateGroups", groups.size());
        result.put("groups", groups.stream().limit(30).toList());
        result.put("notice", "Hashing is read-only. Large files over 100 MB are skipped.");
        return StepOutcome.ok(context, result);
    }

    private StepOutcome readText(WorkflowRun run, WorkflowStep step, WorkflowContext context) {
        RegisteredDirectory directory = requireDirectory(context, run, false);
        Path root = directories.root(directory);
        String relativePath = stringParameter(step.parameters(), "relativePath", "");
        if (relativePath.isBlank()) {
            relativePath = context.textRelativePath() == null ? "" : context.textRelativePath();
        }
        if (relativePath.isBlank()) {
            Object input = run.inputs().get("relativePath");
            if (input instanceof String text && !text.isBlank()) {
                relativePath = text;
            }
        }
        if (relativePath.isBlank()) {
            relativePath = context.selectedFiles().stream()
                    .filter(file -> TEXT_EXTENSIONS.contains(file.extension() == null
                            ? "" : file.extension().toLowerCase(Locale.ROOT)))
                    .map(ContextFile::relativePath)
                    .findFirst()
                    .orElse("");
        }
        if (relativePath.isBlank()) {
            throw ApiException.unprocessable("No readable text file was selected. "
                    + "Add a FILTER_FILES step or set the relativePath parameter.");
        }
        Path file = pathGuard.resolveExistingFile(root, relativePath);
        String extension = Text.extension(file.getFileName().toString());
        if (!TEXT_EXTENSIONS.contains(extension)) {
            throw ApiException.badRequest("Choose a text, log, JSON, CSV, or Markdown file. Binary files are not read.");
        }
        int maxChars = integerParameter(step.parameters(), "maxChars", 20000);
        String excerpt;
        boolean truncated;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            char[] buffer = new char[maxChars + 1];
            int read = reader.read(buffer);
            if (read < 0) {
                read = 0;
            }
            truncated = read > maxChars;
            excerpt = new String(buffer, 0, Math.min(read, maxChars));
        } catch (IOException e) {
            throw ApiException.conflict("The text file could not be read: " + safeMessage(e));
        }
        WorkflowContext updated = context.withText(relativePath);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("relativePath", relativePath);
        result.put("fileName", file.getFileName().toString());
        result.put("characters", excerpt.length());
        result.put("truncated", truncated);
        result.put("excerpt", Text.truncate(excerpt, 4000));
        result.put("notice", "Only an excerpt is stored in history. The file is re-read inside the folder boundary "
                + "when analysis needs it.");
        return StepOutcome.ok(updated, result);
    }

    private StepOutcome analyzeLog(WorkflowRun run, WorkflowStep step, WorkflowContext context) {
        RegisteredDirectory directory = requireDirectory(context, run, false);
        Path root = directories.root(directory);
        String relativePath = context.textRelativePath();
        if (relativePath == null || relativePath.isBlank()) {
            relativePath = context.selectedFiles().stream()
                    .filter(file -> ".log".equalsIgnoreCase(file.extension()))
                    .map(ContextFile::relativePath)
                    .findFirst()
                    .orElse(null);
        }
        if (relativePath == null) {
            throw ApiException.unprocessable("Read a log file before analyzing it. Add READ_APPROVED_TEXT.");
        }
        Path file = pathGuard.resolveExistingFile(root, relativePath);
        LogAnalysis analysis;
        try {
            analysis = logAnalyzer.analyze(file);
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable(e.getMessage());
        }
        boolean useAi = booleanParameter(step.parameters(), "useAi", false);
        List<String> causes = deterministicCauses(analysis);
        List<String> checks = deterministicChecks(analysis);
        String summary = analysis.errorLines() == 0
                ? "No error signatures were found in the readable portion of this log."
                : "GhostShift found " + analysis.errorLines() + " error line(s) across "
                        + analysis.groups().size() + " grouped signature(s).";
        String aiNotice = useAi
                ? "AI interpretation was requested but not used."
                : "AI interpretation was disabled for this step. The report uses deterministic evidence only.";
        boolean aiUsed = false;
        String model = null;
        if (useAi && analysis.errorLines() > 0) {
            try {
                var insight = brain.analyzeLog(analysis, file.getFileName().toString());
                summary = insight.summary();
                if (!insight.possibleCauses().isEmpty()) {
                    causes = insight.possibleCauses();
                }
                if (!insight.recommendedChecks().isEmpty()) {
                    checks = insight.recommendedChecks();
                }
                aiNotice = insight.notice();
                aiUsed = true;
                model = brain.modelName();
            } catch (OllamaClient.AiUnavailableException | OllamaClient.AiInvalidResponseException e) {
                aiNotice = "The local model was not used: " + e.getMessage()
                        + " This report uses deterministic log evidence only.";
            } catch (RuntimeException e) {
                aiNotice = "The local model failed safely. This report uses deterministic evidence only.";
                LOG.warn("Workflow log analysis model failure", e);
            }
        }
        String markdown = renderLogMarkdown(file.getFileName().toString(), analysis, summary,
                causes, checks, aiNotice, aiUsed);
        String reportId = null;
        if (!run.dryRun()) {
            ReportView report = reports.saveWorkflowReport("Incident summary · " + file.getFileName(),
                    markdown, Map.of("workflowRunId", run.id(), "analysis", analysis),
                    aiUsed, model);
            reportId = report.id();
        }
        WorkflowContext updated = context.withAnalysis(analysis, reportId);
        if (analysis.truncated()) {
            updated = updated.withWarning("The log was larger than the read limit; only its tail was analyzed.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("relativePath", relativePath);
        result.put("totalLines", analysis.totalLines());
        result.put("errorLines", analysis.errorLines());
        result.put("warningLines", analysis.warningLines());
        result.put("groups", analysis.groups().size());
        result.put("topSignature", analysis.groups().isEmpty() ? null
                : Text.truncate(analysis.groups().getFirst().sample(), 300));
        result.put("summary", summary);
        result.put("causes", causes);
        result.put("checks", checks);
        result.put("aiUsed", aiUsed);
        result.put("aiNotice", aiNotice);
        result.put("reportId", reportId);
        result.put("dryRun", run.dryRun());
        return StepOutcome.ok(updated, result);
    }

    private StepOutcome generateReport(WorkflowRun run, WorkflowStep step, WorkflowContext context) {
        String title = stringParameter(step.parameters(), "title",
                "Workflow report · " + run.workflowName());
        String markdown = renderWorkflowReport(title, run, context);
        if (run.dryRun()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dryRun", true);
            result.put("title", title);
            result.put("characters", markdown.length());
            result.put("notice", "Dry run: the report was composed but not saved.");
            return StepOutcome.ok(context, result);
        }
        ReportView report = reports.saveWorkflowReport(title, markdown,
                Map.of("workflowRunId", run.id(), "workflow", run.workflowName()), false, null);
        WorkflowContext updated = context.withReport(report.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", report.id());
        result.put("title", report.title());
        result.put("characters", markdown.length());
        return StepOutcome.ok(updated, result);
    }

    private void pauseForApproval(WorkflowRun run, ActionExecution action, WorkflowContext context,
                                  String kind, String title, String explanation, String risk) {
        Approval approval = runs.createApproval(run.id(), action.id(), kind, title, explanation, risk);
        runs.markActionWaiting(action.id());
        runs.markWaiting(run.id(), action.stepIndex());
        runs.addEvent(run.id(), "INFO", "Waiting for approval: " + title,
                Map.of("approvalId", approval.id(), "kind", kind, "risk", risk));
        activity.record("APPROVAL", "Approval requested", run.workflowName() + " · " + title, run.id());
    }

    private void finish(WorkflowRun run, WorkflowDefinition definition) {
        List<ActionExecution> actions = runs.actions(run.id());
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;
        int cancelled = 0;
        for (ActionExecution action : actions) {
            switch (action.status()) {
                case "SUCCEEDED" -> succeeded++;
                case "FAILED" -> failed++;
                case "SKIPPED" -> skipped++;
                case "CANCELLED" -> cancelled++;
                default -> {
                }
            }
        }
        String status = failed == 0 ? "COMPLETED" : (succeeded > 0 ? "PARTIALLY_COMPLETED" : "FAILED");
        StringBuilder summary = new StringBuilder();
        if (run.dryRun()) {
            summary.append("Dry run: no user files were changed. ");
        }
        summary.append(succeeded).append(" step(s) succeeded");
        if (failed > 0) {
            summary.append(", ").append(failed).append(" failed");
        }
        if (skipped > 0) {
            summary.append(", ").append(skipped).append(" skipped");
        }
        if (cancelled > 0) {
            summary.append(", ").append(cancelled).append(" cancelled");
        }
        summary.append(". Policy: ").append(definition.failurePolicy() == null ? "STOP" : definition.failurePolicy())
                .append('.');
        runs.finalizeRun(run.id(), status, summary.toString(), runs.duration(run.id()));
        activity.record("WORKFLOW", run.dryRun() ? "Workflow dry run completed" : "Workflow run completed",
                run.workflowName() + " · " + summary, run.id());
    }

    private void failRun(WorkflowRun run, WorkflowDefinition definition, ActionExecution action,
                         WorkflowStep step, String message) {
        runs.markRemainingSkipped(run.id());
        runs.expireApprovals(run.id());
        String summary = "Step '" + step.id() + "' failed: " + Text.truncate(message, 400)
                + " Remaining steps were skipped.";
        runs.finalizeRun(run.id(), "FAILED", summary, runs.duration(run.id()));
        activity.record("WORKFLOW", "Workflow run failed", run.workflowName() + " · " + summary, run.id());
    }

    private void cancel(String runId, WorkflowRun run, String reason) {
        runs.markRemainingCancelled(runId);
        runs.expireApprovals(runId);
        runs.finalizeRun(runId, "CANCELLED", reason, runs.duration(runId));
        runs.addEvent(runId, "WARN", reason, Map.of());
        activity.record("WORKFLOW", "Workflow run cancelled", run.workflowName() + " · " + reason, runId);
    }

    private void failAction(ActionExecution action, String message) {
        runs.updateAction(action.id(), "FAILED", Map.of(), message, null, Text.now());
    }

    private RegisteredDirectory requireDirectory(WorkflowContext context, WorkflowRun run, boolean write) {
        Long directoryId = context.directoryId();
        if (directoryId == null) {
            directoryId = longValue(run.inputs().get("directoryId"));
        }
        if (directoryId == null) {
            throw ApiException.badRequest("This workflow needs a directoryId input.");
        }
        if (write) {
            return directories.requireOrganize(directoryId);
        }
        directories.requireRead(directoryId);
        return directories.get(directoryId);
    }

    private static boolean isStop(WorkflowDefinition definition) {
        return definition.failurePolicy() == null
                || !definition.failurePolicy().equalsIgnoreCase("CONTINUE_SAFE");
    }

    private static String renderLogMarkdown(String fileName, LogAnalysis analysis, String summary,
                                            List<String> causes, List<String> checks,
                                            String aiNotice, boolean aiUsed) {
        StringBuilder md = new StringBuilder();
        md.append("# Incident summary · ").append(fileName.replace("`", "'")).append("\n\n");
        md.append("**AI used:** ").append(aiUsed ? "yes" : "no").append("  \n");
        md.append("**Evidence:** ").append(analysis.bytesRead()).append(" bytes")
                .append(analysis.truncated() ? " (tail of a larger file)" : "").append("\n\n");
        md.append("## Summary\n\n").append(summary).append("\n\n");
        md.append("## Observed evidence\n\n");
        md.append("- ").append(analysis.totalLines()).append(" line(s), ")
                .append(analysis.errorLines()).append(" error line(s), ")
                .append(analysis.warningLines()).append(" warning line(s).\n");
        for (ErrorGroup group : analysis.groups().stream().limit(10).toList()) {
            md.append("- **").append(group.count()).append(" occurrence(s):** `")
                    .append(group.sample().replace("`", "'")).append("`\n");
        }
        md.append("\n## Possible causes (hypotheses, not confirmed)\n\n");
        for (String cause : causes) {
            md.append("- ").append(cause).append('\n');
        }
        md.append("\n## Recommended checks\n\n");
        int index = 1;
        for (String check : checks) {
            md.append(index++).append(". ").append(check).append('\n');
        }
        md.append("\n## Limitations\n\n")
                .append("These are diagnostic suggestions, not a confirmed root cause. ")
                .append("No commands were executed and no user files were changed.\n");
        if (aiNotice != null && !aiNotice.isBlank()) {
            md.append("\n> ").append(aiNotice.replace("\n", " ")).append('\n');
        }
        return md.toString();
    }

    private String renderWorkflowReport(String title, WorkflowRun run, WorkflowContext context) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");
        md.append("**Workflow:** ").append(run.workflowName()).append("  \n");
        md.append("**Run:** `").append(run.id()).append("`  \n");
        md.append("**Folder:** ").append(context.directoryName() == null ? "—" : context.directoryName())
                .append("  \n");
        md.append("**Dry run:** ").append(run.dryRun() ? "yes" : "no").append("  \n");
        md.append("**Generated:** ").append(Text.now()).append("\n\n");
        md.append("## Files\n\n");
        md.append(context.files().size()).append(" file(s) listed, ")
                .append(context.selectedFiles().size()).append(" selected.\n\n");
        if (!context.classifications().isEmpty()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            context.selectedFiles().forEach(file -> {
                ContextClassification classification = context.classifications().get(file.id());
                if (classification != null) {
                    counts.merge(classification.category().label(), 1, Integer::sum);
                }
            });
            md.append("## Classifications\n\n| Category | Files |\n|---|---:|\n");
            counts.forEach((category, count) -> md.append("| ").append(category)
                    .append(" | ").append(count).append(" |\n"));
            md.append('\n');
        }
        if (!context.proposedMoves().isEmpty()) {
            md.append("## Proposed moves\n\n");
            for (ContextMove move : context.proposedMoves().stream().limit(80).toList()) {
                md.append("- `").append(move.sourceRelative()).append("` → `")
                        .append(move.destinationRelative()).append('`')
                        .append(move.conflict() ? " (destination conflict; will be renamed safely)" : "")
                        .append('\n');
            }
            md.append('\n');
        }
        if (!context.moveResults().isEmpty()) {
            md.append("## Move results\n\n");
            for (ContextMoveResult result : context.moveResults()) {
                md.append("- ").append(result.moved() ? "Moved" : "Not moved").append(": `")
                        .append(result.sourceRelative()).append('`');
                if (result.moved() && result.destinationRelative() != null) {
                    md.append(" → `").append(result.destinationRelative()).append('`');
                }
                if (result.error() != null) {
                    md.append(" — ").append(result.error().replace("\n", " "));
                }
                md.append('\n');
            }
            md.append('\n');
        }
        if (context.analysis() != null) {
            LogAnalysis analysis = context.analysis();
            md.append("## Log analysis\n\n");
            md.append("- File: `").append(analysis.fileName()).append("`\n");
            md.append("- Lines: ").append(analysis.totalLines()).append(", errors: ")
                    .append(analysis.errorLines()).append(", warnings: ")
                    .append(analysis.warningLines()).append('\n');
            for (ErrorGroup group : analysis.groups().stream().limit(10).toList()) {
                md.append("- ").append(group.count()).append("× `")
                        .append(group.sample().replace("`", "'")).append("`\n");
            }
            md.append('\n');
        }
        if (context.warning() != null) {
            md.append("## Warnings\n\n").append(context.warning()).append("\n\n");
        }
        md.append("## Limitations\n\n")
                .append("This report is assembled from GhostShift's local evidence and activity history. ")
                .append("No user files were deleted or overwritten by report generation.");
        if (run.dryRun()) {
            md.append(" This was a dry run; no moves or report files were written to storage.");
        }
        md.append('\n');
        return md.toString();
    }

    private static List<String> deterministicCauses(LogAnalysis analysis) {
        Set<String> causes = new LinkedHashSet<>();
        for (ErrorGroup group : analysis.groups()) {
            String sample = group.sample() == null ? "" : group.sample().toLowerCase(Locale.ROOT);
            if (sample.contains("connection refused") || sample.contains("unreachable")
                    || sample.contains("connect timed out")) {
                causes.add("A dependency (database, API, or network service) may be unavailable.");
            } else if (sample.contains("timed out") || sample.contains("timeout")) {
                causes.add("An operation exceeded its time budget or a dependency is slow.");
            } else if (sample.contains("access denied") || sample.contains("permission")
                    || sample.contains("forbidden") || sample.contains("401") || sample.contains("403")) {
                causes.add("Credentials, permissions, or access policy may be blocking the operation.");
            } else if (sample.contains("out of memory") || sample.contains("heap space")) {
                causes.add("The process may be running out of memory.");
            } else if (sample.contains("no space") || sample.contains("disk full")) {
                causes.add("The storage volume may be full.");
            } else if (sample.contains("locked") || sample.contains("lock")) {
                causes.add("A resource may be locked by another process or transaction.");
            }
            if (causes.size() >= 4) {
                break;
            }
        }
        if (causes.isEmpty()) {
            causes.add(analysis.errorLines() == 0
                    ? "No error evidence was found, so no failure cause is suggested."
                    : "The log shows a recurring fault, but the evidence does not identify its root cause.");
        }
        return new ArrayList<>(causes);
    }

    private static List<String> deterministicChecks(LogAnalysis analysis) {
        List<String> checks = new ArrayList<>();
        if (!analysis.groups().isEmpty()) {
            checks.add("Open the referenced log lines and read the full stack trace for the most frequent signature.");
        }
        checks.add("Reproduce the failure once with debug logging enabled and compare the evidence.");
        checks.add("Verify the external services and configuration this component depends on.");
        checks.add("Check whether failures cluster around a deployment, restart, or configuration change.");
        return checks;
    }

    private static String safeMessage(Throwable e) {
        String message = e.getMessage();
        return Text.truncate(message == null || message.isBlank() ? e.getClass().getSimpleName() : message, 600);
    }

    private static boolean booleanParameter(Map<String, Object> parameters, String key, boolean fallback) {
        Object value = parameters == null ? null : parameters.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static int integerParameter(Map<String, Object> parameters, String key, int fallback) {
        Object value = parameters == null ? null : parameters.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String stringParameter(Map<String, Object> parameters, String key, String fallback) {
        Object value = parameters == null ? null : parameters.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static String logTarget(WorkflowContext context) {
        if (context.textRelativePath() != null && !context.textRelativePath().isBlank()) {
            return "`" + context.textRelativePath() + "`";
        }
        return context.selectedFiles().stream()
                .filter(file -> ".log".equalsIgnoreCase(file.extension()))
                .map(file -> "`" + file.relativePath() + "`")
                .findFirst()
                .orElse("the selected log");
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record StepOutcome(WorkflowContext context, Map<String, Object> result,
                               boolean failed, String error) {
        static StepOutcome ok(WorkflowContext context, Map<String, Object> result) {
            return new StepOutcome(context, result, false, null);
        }
    }
}
