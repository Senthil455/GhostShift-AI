package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.config.AppConfig;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.Category;
import ai.ghostshift.domain.Classification;
import ai.ghostshift.domain.FileInfo;
import ai.ghostshift.domain.OrganizationPlan;
import ai.ghostshift.domain.OrganizationPlanRequest;
import ai.ghostshift.domain.OrganizationPlanSummary;
import ai.ghostshift.domain.PlanAction;
import ai.ghostshift.domain.PlanDecision;
import ai.ghostshift.domain.PlanDecisionRequest;
import ai.ghostshift.domain.PlanPatchRequest;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.domain.Statuses;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.PathGuard;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GhostSort: scan -> classify -> preview -> approve -> execute -> record -> undo.
 * The model never decides paths. Destinations are derived by application code.
 */
@Singleton
public class OrganizationService {
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationService.class);

    private final Database db;
    private final Json json;
    private final AppConfig config;
    private final DirectoryService directories;
    private final FileScanner scanner;
    private final ClassificationService classification;
    private final ClassificationMemoryService memory;
    private final SettingsService settings;
    private final SafeFileOperations fileOperations;
    private final PathGuard pathGuard;
    private final ActivityService activity;
    private final JobRunner jobs;

    public OrganizationService(Database db, Json json, AppConfig config, DirectoryService directories,
                               FileScanner scanner, ClassificationService classification,
                               ClassificationMemoryService memory, SettingsService settings,
                               SafeFileOperations fileOperations, PathGuard pathGuard,
                               ActivityService activity, JobRunner jobs) {
        this.db = db;
        this.json = json;
        this.config = config;
        this.directories = directories;
        this.scanner = scanner;
        this.classification = classification;
        this.memory = memory;
        this.settings = settings;
        this.fileOperations = fileOperations;
        this.pathGuard = pathGuard;
        this.activity = activity;
        this.jobs = jobs;
    }

    public OrganizationPlan createPlan(OrganizationPlanRequest request) {
        if (request == null) {
            throw ApiException.badRequest("An organization request is required.");
        }
        RegisteredDirectory directory = directories.requireOrganize(request.directoryId());
        boolean useAi = request.useAi() == null || request.useAi();
        FileScanner.Scan scan = scanner.scan(directory, Boolean.TRUE.equals(request.recursive()), 0);
        if (scan.files().isEmpty()) {
            throw ApiException.unprocessable("No files were found in that folder. Add files or choose another folder.");
        }
        ClassificationService.Outcome outcome = classification.classify(scan.entries(), useAi);
        Path root = scan.root();
        String planId = UUID.randomUUID().toString();
        String now = Text.now();
        List<PlanAction> actions = new ArrayList<>();
        Set<String> proposedDestinations = new LinkedHashSet<>();
        int conflicts = 0;

        for (FileScanner.ScannedFile file : scan.entries()) {
            Classification result = outcome.get(file.id());
            String destination = destinationFor(result.category(), file.name());
            boolean conflict = proposedDestinations.contains(destination)
                    || Files.exists(pathGuard.resolveForWrite(root, destination), LinkOption.NOFOLLOW_LINKS);
            if (conflict) {
                conflicts++;
            }
            String actionId = UUID.randomUUID().toString();
            actions.add(new PlanAction(
                    actionId,
                    file.relativePath(),
                    destination,
                    file.name(),
                    file.extension(),
                    file.sizeBytes(),
                    file.modifiedAt(),
                    result.category(),
                    result.confidence(),
                    result.rationale(),
                    result.source(),
                    Statuses.PlanActionStatus.PROPOSED.name(),
                    conflict,
                    null
            ));
            proposedDestinations.add(destination);
        }

        String summary = buildPlanSummary(actions, conflicts, outcome, scan.truncated());
        int actionCount = actions.size();
        int conflictCount = conflicts;
        String storedSummary = summary;
        db.inTransaction(connection -> {
            Database.execute(connection, """
                            INSERT INTO organization_plan
                                (id, directory_id, status, file_count, conflict_count, summary, created_at, updated_at)
                            VALUES (?, ?, 'READY_FOR_REVIEW', ?, ?, ?, ?, ?)
                            """, planId, directory.id(), actionCount, conflictCount, storedSummary, now, now);
            for (PlanAction action : actions) {
                Database.execute(connection, """
                                INSERT INTO organization_action
                                    (id, plan_id, source_relative, destination_relative, file_name, extension,
                                     size_bytes, modified_at, category, confidence, rationale, classification_source,
                                     status, conflict, error)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """, action.id(), planId, action.sourceRelative(), action.destinationRelative(),
                        action.fileName(), action.extension(), action.sizeBytes(), action.modifiedAt(),
                        action.category().name(), action.confidence(), action.rationale(),
                        action.classificationSource(), action.status(), action.conflict() ? 1 : 0, null);
            }
            return null;
        });
        if (scan.truncated()) {
            summary += " The folder was larger than the scan limit (" + config.maxScanFiles() + " files); only the first files were included.";
        }
        activity.record("ORGANIZATION", "Organization plan created",
                directory.displayName() + " · " + actions.size() + " files · " + conflicts + " conflicts", planId);
        return getPlan(planId);
    }

    public OrganizationPlan getPlan(String planId) {
        OrganizationPlanSummary summary = db.queryOne("""
                        SELECT p.id, p.directory_id, d.display_name, d.canonical_path, p.status,
                               p.file_count, p.conflict_count, p.summary, p.created_at, p.updated_at, p.executed_at
                        FROM organization_plan p
                        JOIN registered_directory d ON d.id = p.directory_id
                        WHERE p.id = ?
                        """, rs -> new OrganizationPlanSummary(
                        rs.getString("id"), rs.getLong("directory_id"), rs.getString("display_name"),
                        rs.getString("status"), rs.getInt("file_count"), rs.getInt("conflict_count"),
                        rs.getString("summary"), rs.getString("created_at"), rs.getString("updated_at"),
                        rs.getString("executed_at")), planId)
                .orElseThrow(() -> ApiException.notFound("That organization plan does not exist."));
        String directoryPath = db.queryOne("SELECT canonical_path FROM registered_directory WHERE id = ?",
                rs -> rs.getString(1), summary.directoryId()).orElse("");
        List<PlanAction> actions = actions(planId);
        return new OrganizationPlan(summary.id(), summary.directoryId(), summary.directoryName(), directoryPath,
                summary.status(), summary.fileCount(), summary.conflictCount(), summary.summary(),
                summary.createdAt(), summary.updatedAt(), summary.executedAt(), actions);
    }

    public List<OrganizationPlanSummary> listPlans(int limit) {
        int bounded = Math.clamp(limit, 1, 100);
        return db.query("""
                        SELECT p.id, p.directory_id, d.display_name, p.status, p.file_count, p.conflict_count,
                               p.summary, p.created_at, p.updated_at, p.executed_at
                        FROM organization_plan p
                        JOIN registered_directory d ON d.id = p.directory_id
                        ORDER BY p.created_at DESC LIMIT ?
                        """, rs -> new OrganizationPlanSummary(
                        rs.getString("id"), rs.getLong("directory_id"), rs.getString("display_name"),
                        rs.getString("status"), rs.getInt("file_count"), rs.getInt("conflict_count"),
                        rs.getString("summary"), rs.getString("created_at"), rs.getString("updated_at"),
                        rs.getString("executed_at")), bounded);
    }

    public OrganizationPlan patchAction(String planId, String actionId, PlanPatchRequest request) {
        OrganizationPlan plan = getPlan(planId);
        if (!plan.status().equals("READY_FOR_REVIEW") && !plan.status().equals("APPROVED")) {
            throw ApiException.conflict("This plan can no longer be edited. Create a new plan.");
        }
        PlanAction action = plan.actions().stream().filter(item -> item.id().equals(actionId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("That file action is not part of this plan."));
        Category category = request.category() == null ? action.category() : request.category();
        String status = request.status() == null ? Statuses.PlanActionStatus.PROPOSED.name()
                : normalizeActionStatus(request.status());
        String destination = destinationFor(category, action.fileName());
        boolean conflict = false;
        try {
            Path root = directories.root(directories.get(plan.directoryId()));
            conflict = Files.exists(pathGuard.resolveForWrite(root, destination), LinkOption.NOFOLLOW_LINKS);
        } catch (RuntimeException ignored) {
            conflict = true;
        }
        String rationale = request.category() == null ? action.rationale() : "Category changed by the user.";
        String source = request.category() == null ? action.classificationSource() : "USER";
        double confidence = request.category() == null ? action.confidence() : 1.0;
        boolean categoryChanged = request.category() != null && request.category() != action.category();
        db.update("""
                        UPDATE organization_action
                        SET category = ?, destination_relative = ?, status = ?, conflict = ?,
                            rationale = ?, classification_source = ?, confidence = ?
                        WHERE plan_id = ? AND id = ?
                        """, category.name(), destination, status, conflict ? 1 : 0,
                rationale, source, confidence, planId, actionId);
        if (Boolean.TRUE.equals(request.remember())) {
            memory.remember(action.fileName(), category);
        }
        if (categoryChanged || request.status() != null) {
            db.update("UPDATE organization_plan SET status = 'READY_FOR_REVIEW', updated_at = ? WHERE id = ?",
                    Text.now(), planId);
        }
        return getPlan(planId);
    }

    public OrganizationPlan decide(String planId, PlanDecisionRequest request) {
        OrganizationPlan plan = getPlan(planId);
        if (plan.status().equals("EXECUTING") || plan.status().equals("COMPLETED")
                || plan.status().equals("PARTIALLY_COMPLETED") || plan.status().equals("CANCELLED")
                || plan.status().equals("UNDONE")) {
            throw ApiException.conflict("This plan has already been executed or closed.");
        }
        Map<String, PlanAction> byId = new LinkedHashMap<>();
        plan.actions().forEach(action -> byId.put(action.id(), action));
        List<PlanDecision> decisions = request == null || request.decisions() == null
                ? plan.actions().stream().map(action -> new PlanDecision(action.id(), "APPROVE", null, false)).toList()
                : request.decisions();
        if (decisions.isEmpty()) {
            throw ApiException.badRequest("Provide at least one approval decision.");
        }
        int approved = 0;
        for (PlanDecision decision : decisions) {
            PlanAction action = byId.get(decision.actionId());
            if (action == null) {
                throw ApiException.badRequest("Unknown plan action: " + decision.actionId());
            }
            if (decision.category() != null) {
                // Route category edits through the same validation used by PATCH.
                patchAction(planId, action.id(),
                        new PlanPatchRequest(decision.category(), null, decision.remember()));
            }
            String status = switch (decision.decision() == null ? "" : decision.decision().toUpperCase()) {
                case "APPROVE", "APPROVED" -> Statuses.PlanActionStatus.APPROVED.name();
                case "REJECT", "REJECTED" -> Statuses.PlanActionStatus.REJECTED.name();
                default -> throw ApiException.badRequest("Each decision must be APPROVE or REJECT.");
            };
            db.update("UPDATE organization_action SET status = ? WHERE plan_id = ? AND id = ?",
                    status, planId, action.id());
            if (status.equals("APPROVED")) {
                approved++;
            }
            if (Boolean.TRUE.equals(decision.remember()) && decision.category() != null) {
                memory.remember(action.fileName(), decision.category());
            }
        }
        if (Boolean.TRUE.equals(request != null ? request.rejectUnreviewed() : null)) {
            db.update("""
                    UPDATE organization_action SET status = 'REJECTED'
                    WHERE plan_id = ? AND status = 'PROPOSED'
                    """, planId);
        }
        String status = approved > 0 ? "APPROVED" : "READY_FOR_REVIEW";
        db.update("UPDATE organization_plan SET status = ?, updated_at = ? WHERE id = ?",
                status, Text.now(), planId);
        activity.record("ORGANIZATION", "Organization plan reviewed",
                plan.actions().size() + " files · " + approved + " approved", planId);
        return getPlan(planId);
    }

    public OrganizationPlan execute(String planId) {
        OrganizationPlan plan = getPlan(planId);
        if (plan.status().equals("EXECUTING")) {
            throw ApiException.conflict("This plan is already executing.");
        }
        if (!plan.status().equals("APPROVED")) {
            if (plan.status().equals("READY_FOR_REVIEW") && !settings.approvals().fileMovesRequireApproval()) {
                decide(planId, null);
            } else {
                throw ApiException.conflict("Review and approve the files before executing this plan.");
            }
        }
        int approved = (int) plan.actions().stream()
                .filter(action -> action.status().equals("APPROVED")).count();
        if (approved == 0) {
            throw ApiException.conflict("No files were approved. Nothing to execute.");
        }
        db.update("""
                UPDATE organization_plan SET status = 'EXECUTING', executed_at = ?, updated_at = ? WHERE id = ?
                """, Text.now(), Text.now(), planId);
        jobs.submit("organization plan " + planId, () -> runPlan(planId));
        return getPlan(planId);
    }

    public OrganizationPlan cancel(String planId) {
        OrganizationPlan plan = getPlan(planId);
        if (plan.status().equals("COMPLETED") || plan.status().equals("CANCELLED")
                || plan.status().equals("UNDONE")) {
            return plan;
        }
        db.update("UPDATE organization_plan SET status = 'CANCELLED', updated_at = ? WHERE id = ?",
                Text.now(), planId);
        activity.record("ORGANIZATION", "Organization plan cancelled",
                plan.directoryName() + " · completed moves are not reversed automatically", planId);
        return getPlan(planId);
    }

    public OrganizationPlan undo(String planId) {
        OrganizationPlan plan = getPlan(planId);
        if (!plan.status().equals("COMPLETED") && !plan.status().equals("PARTIALLY_COMPLETED")) {
            throw ApiException.conflict("Only a completed or partially completed plan can be undone.");
        }
        RegisteredDirectory directory = directories.requireOrganize(plan.directoryId());
        Path root = directories.root(directory);
        List<FileOp> operations = db.query("""
                        SELECT id, original_path, destination_path, recovery_json, status
                        FROM file_operation
                        WHERE plan_id = ? AND operation_type = 'MOVE' AND status = 'COMPLETED'
                        ORDER BY created_at DESC
                        """, rs -> new FileOp(rs.getString("id"), rs.getString("original_path"),
                        rs.getString("destination_path"), rs.getString("recovery_json"), rs.getString("status")),
                planId);
        int restored = 0;
        int failed = 0;
        for (FileOp operation : operations) {
            Map<String, Object> recovery;
            try {
                recovery = json.toObject(operation.recoveryJson());
            } catch (RuntimeException e) {
                failed++;
                markOperationUndoFailed(operation.id(), "Recovery metadata is unreadable.");
                continue;
            }
            String currentRelative = String.valueOf(recovery.get("movedToRelative"));
            String originalRelative = String.valueOf(recovery.get("originalRelative"));
            try {
                fileOperations.restoreVerified(root, currentRelative, originalRelative, recovery);
                db.update("""
                        UPDATE file_operation SET status = 'UNDONE', undone_at = ? WHERE id = ?
                        """, Text.now(), operation.id());
                db.update("""
                        UPDATE organization_action SET status = 'UNDONE'
                        WHERE plan_id = ? AND destination_relative = ?
                        """, planId, currentRelative);
                restored++;
            } catch (RuntimeException e) {
                failed++;
                markOperationUndoFailed(operation.id(), e.getMessage());
                LOG.info("Undo refused for plan {} operation {}: {}", planId, operation.id(), e.getMessage());
            }
        }
        String status = failed == 0 ? "UNDONE" : "PARTIALLY_COMPLETED";
        String summary = restored + " file(s) restored."
                + (failed > 0 ? " " + failed + " file(s) could not be restored safely; review the activity details." : "");
        db.update("UPDATE organization_plan SET status = ?, summary = ?, updated_at = ? WHERE id = ?",
                status, summary, Text.now(), planId);
        activity.record("ORGANIZATION", failed == 0 ? "Organization undone" : "Organization partly undone",
                plan.directoryName() + " · " + summary, planId);
        return getPlan(planId);
    }

    private void markOperationUndoFailed(String operationId, String error) {
        db.update("UPDATE file_operation SET status = 'UNDO_FAILED', error = ? WHERE id = ?",
                Text.truncate(error, 400), operationId);
    }

    private void runPlan(String planId) {
        OrganizationPlan plan;
        RegisteredDirectory directory;
        Path root;
        try {
            plan = getPlan(planId);
            directory = directories.requireOrganize(plan.directoryId());
            root = directories.root(directory);
        } catch (RuntimeException e) {
            failPlan(planId, "The registered folder is no longer available. " + e.getMessage());
            return;
        }
        int moved = 0;
        int failed = 0;
        int skipped = 0;
        List<PlanAction> actions = plan.actions().stream()
                .filter(action -> action.status().equals("APPROVED"))
                .toList();
        for (PlanAction action : actions) {
            String currentStatus = db.queryOne("SELECT status FROM organization_plan WHERE id = ?",
                    rs -> rs.getString(1), planId).orElse("FAILED");
            if (currentStatus.equals("CANCELLED")) {
                skipRemaining(planId);
                finishPlan(planId, "CANCELLED", moved, failed,
                        "Cancelled by the user after " + moved + " safe move(s).", true);
                return;
            }
            try {
                SafeFileOperations.MoveOutcome outcome = fileOperations.moveVerified(
                        root, action.sourceRelative(), action.destinationRelative(),
                        action.sizeBytes(), action.modifiedAt());
                String operationId = UUID.randomUUID().toString();
                db.update("""
                                INSERT INTO file_operation
                                    (id, plan_id, original_path, destination_path, operation_type, status,
                                     recovery_json, created_at)
                                VALUES (?, ?, ?, ?, 'MOVE', 'COMPLETED', ?, ?)
                                """, operationId, planId,
                        root.resolve(action.sourceRelative()).toAbsolutePath().toString(),
                        outcome.absoluteDestination(), json.write(outcome.recovery()), Text.now());
                db.update("""
                        UPDATE organization_action SET status = 'MOVED', destination_relative = ?, error = NULL
                        WHERE plan_id = ? AND id = ?
                        """, outcome.actualRelative(), planId, action.id());
                moved++;
            } catch (RuntimeException e) {
                failed++;
                db.update("""
                        UPDATE organization_action SET status = 'FAILED', error = ? WHERE plan_id = ? AND id = ?
                        """, Text.truncate(e.getMessage(), 500), planId, action.id());
            }
        }
        String status = failed == 0 ? "COMPLETED" : moved > 0 ? "PARTIALLY_COMPLETED" : "FAILED";
        String summary = moved + " file(s) organized safely."
                + (failed > 0 ? " " + failed + " failed and were left untouched." : "")
                + (skipped > 0 ? " " + skipped + " skipped." : "");
        finishPlan(planId, status, moved, failed, summary, false);
    }

    private void finishPlan(String planId, String status, int moved, int failed, String summary, boolean cancelled) {
        db.update("UPDATE organization_plan SET status = ?, summary = ?, updated_at = ? WHERE id = ?",
                status, Text.truncate(summary, 1000), Text.now(), planId);
        activity.record("ORGANIZATION", cancelled ? "Organization cancelled" : "Organization executed",
                summary, planId);
    }

    private void failPlan(String planId, String message) {
        db.update("UPDATE organization_plan SET status = 'FAILED', summary = ?, updated_at = ? WHERE id = ?",
                Text.truncate(message, 1000), Text.now(), planId);
    }

    private void skipRemaining(String planId) {
        db.update("""
                UPDATE organization_action SET status = 'SKIPPED'
                WHERE plan_id = ? AND status = 'APPROVED'
                """, planId);
    }

    private List<PlanAction> actions(String planId) {
        return db.query("""
                        SELECT id, source_relative, destination_relative, file_name, extension, size_bytes,
                               modified_at, category, confidence, rationale, classification_source, status,
                               conflict, error
                        FROM organization_action WHERE plan_id = ? ORDER BY source_relative
                        """, rs -> new PlanAction(
                        rs.getString("id"), rs.getString("source_relative"), rs.getString("destination_relative"),
                        rs.getString("file_name"), rs.getString("extension"), rs.getLong("size_bytes"),
                        rs.getString("modified_at"), Category.valueOf(rs.getString("category")),
                        rs.getDouble("confidence"), rs.getString("rationale"), rs.getString("classification_source"),
                        rs.getString("status"), rs.getInt("conflict") == 1, rs.getString("error")), planId);
    }

    private String destinationFor(Category category, String fileName) {
        String folder = settings.folderFor(category);
        return FileScanner.ORGANIZER_FOLDER + "/" + folder + "/" + fileName;
    }

    private String buildPlanSummary(List<PlanAction> actions, int conflicts,
                                    ClassificationService.Outcome outcome, boolean truncated) {
        long ai = actions.stream().filter(action -> action.classificationSource().equals("AI")).count();
        long memory = actions.stream().filter(action -> action.classificationSource().equals("MEMORY")).count();
        StringBuilder summary = new StringBuilder();
        summary.append(actions.size()).append(" file(s) classified for review. ");
        summary.append(conflicts).append(" destination conflict(s) will be renamed safely. ");
        if (ai > 0) {
            summary.append(ai).append(" classification(s) used the local model. ");
        }
        if (memory > 0) {
            summary.append(memory).append(" used your remembered corrections. ");
        }
        if (outcome.warning() != null) {
            summary.append(outcome.warning()).append(' ');
        }
        if (truncated) {
            summary.append("The scan limit was reached. ");
        }
        summary.append("No files have been moved yet.");
        return Text.truncate(summary.toString(), 1000);
    }

    private static String normalizeActionStatus(String status) {
        String normalized = status.toUpperCase();
        if (normalized.equals("APPROVE")) {
            normalized = "APPROVED";
        } else if (normalized.equals("REJECT")) {
            normalized = "REJECTED";
        }
        return switch (normalized) {
            case "PROPOSED", "APPROVED", "REJECTED" -> normalized;
            default -> throw ApiException.badRequest("Action status must be PROPOSED, APPROVED, or REJECTED.");
        };
    }

    private record FileOp(String id, String originalPath, String destinationPath, String recoveryJson, String status) {
    }
}
