package ai.ghostshift.service;

import ai.ghostshift.ai.GhostBrainService;
import ai.ghostshift.ai.OllamaClient;
import ai.ghostshift.api.ApiException;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.AiWorkflowDraft;
import ai.ghostshift.domain.Statuses;
import ai.ghostshift.domain.WorkflowDefinition;
import ai.ghostshift.domain.WorkflowDetail;
import ai.ghostshift.domain.WorkflowInput;
import ai.ghostshift.domain.WorkflowRequest;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.domain.WorkflowStep;
import ai.ghostshift.domain.WorkflowSummary;
import ai.ghostshift.domain.WorkflowValidation;
import ai.ghostshift.domain.WorkflowVersionView;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.Text;
import ai.ghostshift.workflow.ActionRegistry;
import ai.ghostshift.workflow.WorkflowValidator;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Workflow definitions are versioned and validated before anything can run.
 * A workflow edit never silently changes an approved automation: editing an
 * approved definition resets it to review.
 */
@Singleton
public class WorkflowService {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowService.class);
    private static final Set<String> CATEGORIES = Set.of(
            "GENERAL", "FILE_ORGANIZATION", "LOG_ANALYSIS", "DEVELOPER", "CUSTOM");
    private static final Set<String> DIRECTORY_ACTIONS = Set.of(
            "LIST_FILES", "READ_APPROVED_TEXT", "ANALYZE_LOG", "MOVE_FILE");

    private final Database db;
    private final Json json;
    private final WorkflowValidator validator;
    private final ActionRegistry registry;
    private final GhostBrainService brain;
    private final ActivityService activity;

    public WorkflowService(Database db, Json json, WorkflowValidator validator, ActionRegistry registry,
                           GhostBrainService brain, ActivityService activity) {
        this.db = db;
        this.json = json;
        this.validator = validator;
        this.registry = registry;
        this.brain = brain;
        this.activity = activity;
    }

    public List<WorkflowSummary> list(boolean includeArchived) {
        String sql = """
                SELECT id, name, description, category, current_version, status, archived, created_at, updated_at
                FROM workflow
                """ + (includeArchived ? "" : " WHERE archived = 0") + " ORDER BY updated_at DESC";
        List<WorkflowRow> rows = db.query(sql, this::mapRow);
        List<WorkflowSummary> result = new ArrayList<>();
        for (WorkflowRow row : rows) {
            DefinitionView view = definitionView(row);
            result.add(new WorkflowSummary(row.id(), row.name(), row.description(), row.category(),
                    row.currentVersion(), row.status(), row.archived(), row.createdAt(), row.updatedAt(),
                    view.definition().steps() == null ? 0 : view.definition().steps().size(),
                    view.validation().requiredPermissions(), view.validation()));
        }
        return result;
    }

    public WorkflowDetail get(String id) {
        WorkflowRow row = requireRow(id);
        DefinitionView current = definitionView(row);
        return new WorkflowDetail(row.id(), row.name(), row.description(), row.category(),
                row.currentVersion(), row.status(), row.archived(), row.createdAt(), row.updatedAt(),
                current.definition(), current.validation(), versions(row.id()));
    }

    public WorkflowDetail create(WorkflowRequest request) {
        if (request == null || request.definition() == null) {
            throw ApiException.badRequest("A workflow definition is required.");
        }
        Normalized normalized = normalize(request);
        WorkflowValidation validation = validator.validate(normalized.definition());
        String id = UUID.randomUUID().toString();
        String now = Text.now();
        String status = validation.valid() ? Statuses.WorkflowStatus.READY_FOR_REVIEW.name()
                : Statuses.WorkflowStatus.DRAFT.name();
        db.inTransaction(connection -> {
            Database.execute(connection, """
                            INSERT INTO workflow
                                (id, name, description, category, current_version, status, archived, created_at, updated_at)
                            VALUES (?, ?, ?, ?, 1, ?, 0, ?, ?)
                            """, id, normalized.name(), normalized.description(), normalized.category(),
                    status, now, now);
            Database.execute(connection, """
                            INSERT INTO workflow_version
                                (id, workflow_id, version, schema_version, definition_json, validation_json, created_at)
                            VALUES (?, ?, 1, 1, ?, ?, ?)
                            """, UUID.randomUUID().toString(), id, json.write(normalized.definition()),
                    json.write(validation), now);
            return null;
        });
        activity.record("WORKFLOW", "Workflow created", normalized.name() + " · version 1", id);
        return get(id);
    }

    public WorkflowDetail createApproved(String name, String description, String category,
                                         WorkflowDefinition definition) {
        WorkflowValidation validation = validator.validate(definition);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Seeded workflow is invalid: " + String.join(" ", validation.errors()));
        }
        WorkflowRequest request = new WorkflowRequest(name, description, category, definition);
        Normalized normalized = normalize(request);
        String id = UUID.randomUUID().toString();
        String now = Text.now();
        db.inTransaction(connection -> {
            Database.execute(connection, """
                            INSERT INTO workflow
                                (id, name, description, category, current_version, status, archived, created_at, updated_at)
                            VALUES (?, ?, ?, ?, 1, 'APPROVED', 0, ?, ?)
                            """, id, normalized.name(), normalized.description(), normalized.category(), now, now);
            Database.execute(connection, """
                            INSERT INTO workflow_version
                                (id, workflow_id, version, schema_version, definition_json, validation_json, created_at)
                            VALUES (?, ?, 1, 1, ?, ?, ?)
                            """, UUID.randomUUID().toString(), id, json.write(normalized.definition()),
                    json.write(validation), now);
            return null;
        });
        activity.record("WORKFLOW", "Example workflow added", normalized.name(), id);
        return get(id);
    }

    public WorkflowDetail update(String id, WorkflowRequest request) {
        WorkflowRow current = requireRow(id);
        if (request == null || request.definition() == null) {
            throw ApiException.badRequest("A workflow definition is required.");
        }
        Normalized normalized = normalize(request);
        WorkflowValidation validation = validator.validate(normalized.definition());
        DefinitionView old = definitionView(current);
        boolean definitionChanged = !json.write(old.definition()).equals(json.write(normalized.definition()));
        boolean wasApproved = current.status().equals(Statuses.WorkflowStatus.APPROVED.name());
        String status = current.status();
        if (definitionChanged && wasApproved) {
            status = Statuses.WorkflowStatus.READY_FOR_REVIEW.name();
        } else if (definitionChanged && current.status().equals(Statuses.WorkflowStatus.DRAFT.name())
                && validation.valid()) {
            status = Statuses.WorkflowStatus.READY_FOR_REVIEW.name();
        }
        String now = Text.now();
        int newVersion = definitionChanged ? current.currentVersion() + 1 : current.currentVersion();
        String finalStatus = status;
        db.inTransaction(connection -> {
            if (definitionChanged) {
                Database.execute(connection, """
                                INSERT INTO workflow_version
                                    (id, workflow_id, version, schema_version, definition_json, validation_json, created_at)
                                VALUES (?, ?, ?, 1, ?, ?, ?)
                                """, UUID.randomUUID().toString(), id, current.currentVersion() + 1,
                        json.write(normalized.definition()), json.write(validation), now);
            }
            Database.execute(connection, """
                            UPDATE workflow
                            SET name = ?, description = ?, category = ?, current_version = ?, status = ?,
                                updated_at = ?
                            WHERE id = ?
                            """, normalized.name(), normalized.description(), normalized.category(),
                    newVersion, finalStatus, now, id);
            return null;
        });
        if (definitionChanged) {
            activity.record("WORKFLOW", "Workflow edited",
                    normalized.name() + " · version " + newVersion
                            + (wasApproved ? " · approval reset for review" : ""), id);
        }
        return get(id);
    }

    public WorkflowDetail setStatus(String id, String requestedStatus) {
        WorkflowRow row = requireRow(id);
        String status = requestedStatus == null ? "" : requestedStatus.strip().toUpperCase(Locale.ROOT);
        WorkflowValidation validation = definitionView(row).validation();
        switch (status) {
            case "DRAFT", "DISABLED" -> {
            }
            case "READY_FOR_REVIEW" -> {
                if (!validation.valid()) {
                    throw ApiException.conflict("Fix the validation errors before this workflow can be reviewed.");
                }
            }
            case "APPROVED" -> {
                if (!validation.valid()) {
                    throw ApiException.conflict("Fix the validation errors before approving this workflow.");
                }
                if (row.archived()) {
                    throw ApiException.conflict("Restore this workflow before approving it.");
                }
            }
            case "ARCHIVED" -> {
                return archive(id);
            }
            default -> throw ApiException.badRequest(
                    "Workflow status must be DRAFT, READY_FOR_REVIEW, APPROVED, DISABLED, or ARCHIVED.");
        }
        db.update("UPDATE workflow SET status = ?, archived = 0, updated_at = ? WHERE id = ?",
                status, Text.now(), id);
        activity.record("WORKFLOW", "Workflow status changed", row.name() + " · " + status, id);
        return get(id);
    }

    public WorkflowDetail archive(String id) {
        WorkflowRow row = requireRow(id);
        db.update("""
                UPDATE workflow SET status = 'ARCHIVED', archived = 1, updated_at = ? WHERE id = ?
                """, Text.now(), id);
        activity.record("WORKFLOW", "Workflow archived", row.name() + " · runs and versions are preserved", id);
        return get(id);
    }

    public WorkflowDetail restore(String id) {
        WorkflowRow row = requireRow(id);
        WorkflowValidation validation = definitionView(row).validation();
        String status = validation.valid() ? "READY_FOR_REVIEW" : "DRAFT";
        db.update("UPDATE workflow SET status = ?, archived = 0, updated_at = ? WHERE id = ?",
                status, Text.now(), id);
        activity.record("WORKFLOW", "Workflow restored", row.name() + " · " + status, id);
        return get(id);
    }

    public WorkflowValidation validate(WorkflowDefinition definition) {
        return validator.validate(definition);
    }

    public AiWorkflowDraft generateDraft(String prompt, Long directoryId) {
        if (prompt == null || prompt.isBlank()) {
            throw ApiException.badRequest("Describe the task you want to automate.");
        }
        if (prompt.length() > 4000) {
            throw ApiException.badRequest("Keep the task description under 4,000 characters.");
        }
        String raw;
        try {
            raw = brain.generateWorkflowJson(prompt, registry.catalog());
        } catch (OllamaClient.AiUnavailableException e) {
            throw ApiException.serviceUnavailable("GhostBrain could not draft this workflow: " + e.getMessage()
                    + " Nothing was saved. You can build the workflow manually or start Ollama.");
        } catch (OllamaClient.AiInvalidResponseException e) {
            throw ApiException.unprocessable("The local model returned an unusable workflow draft: "
                    + e.getMessage() + " Nothing was saved.");
        } catch (RuntimeException e) {
            LOG.warn("Workflow draft failed safely", e);
            throw ApiException.serviceUnavailable(
                    "GhostBrain failed safely while drafting. Nothing was saved. Try again or build it manually.");
        }
        WorkflowDefinition definition = parseDraft(raw);
        if (directoryId != null) {
            definition = ensureDirectoryInput(definition);
        }
        WorkflowValidation validation = validator.validate(definition);
        String notice = "Drafted by the local model " + brain.modelName()
                + ". Nothing runs automatically: review every step, fix validation issues, save, then approve.";
        return new AiWorkflowDraft(definition, notice, validation, true);
    }

    public WorkflowDefinition definitionForRun(WorkflowRun run) {
        for (WorkflowVersionView version : versions(run.workflowId())) {
            if (version.version() == run.workflowVersion()) {
                return definitionByVersion(run.workflowId(), run.workflowVersion());
            }
        }
        return definitionByVersion(run.workflowId(), run.workflowVersion());
    }

    public WorkflowDefinition definitionByVersion(String workflowId, int version) {
        String definitionJson = db.queryOne("""
                        SELECT definition_json FROM workflow_version WHERE workflow_id = ? AND version = ?
                        """, rs -> rs.getString(1), workflowId, version)
                .orElseThrow(() -> ApiException.conflict(
                        "The workflow version used by this run is no longer available."));
        try {
            return json.read(definitionJson, WorkflowDefinition.class);
        } catch (RuntimeException e) {
            throw ApiException.conflict("The stored workflow definition is unreadable.");
        }
    }

    public boolean existsByName(String name) {
        return db.queryOne("SELECT 1 FROM workflow WHERE name = ? AND archived = 0",
                rs -> rs.getInt(1), name).isPresent();
    }

    public long count() {
        return db.queryOne("SELECT COUNT(*) FROM workflow WHERE archived = 0", rs -> rs.getLong(1)).orElse(0L);
    }

    public void deleteAll() {
        db.update("DELETE FROM workflow");
        db.update("DELETE FROM recording");
    }

    /** Creates the built-in examples only when their names are not present. Idempotent. */
    public int seedExamples() {
        int created = 0;
        for (ExampleWorkflow example : exampleWorkflows()) {
            if (existsByName(example.name())) {
                continue;
            }
            try {
                createApproved(example.name(), example.description(), example.category(), example.definition());
                created++;
            } catch (RuntimeException e) {
                LOG.warn("Could not seed example workflow '{}': {}", example.name(), e.getMessage());
            }
        }
        return created;
    }

    public List<ExampleWorkflow> exampleWorkflows() {
        WorkflowInput directoryInput = new WorkflowInput("directoryId", "Folder",
                "DIRECTORY", true, "The registered folder this workflow may read or organize.");

        WorkflowDefinition tidy = new WorkflowDefinition(1, "Tidy a folder (example)",
                "Lists files, classifies them with deterministic rules, previews safe destination paths, "
                        + "asks for approval, then moves approved files without overwriting anything.",
                List.of(
                        step("list", "LIST_FILES", "List the files in the folder.",
                                Map.of("recursive", false, "maxFiles", 100)),
                        step("classify", "CLASSIFY_FILES", "Apply deterministic filename rules. AI is off in this example.",
                                Map.of("useAi", false)),
                        step("propose", "PROPOSE_DESTINATION", "Compute destinations inside GhostShift Organizer.",
                                Map.of()),
                        step("preview", "PREVIEW_FILE_MOVE", "Check boundaries and destination conflicts.",
                                Map.of()),
                        step("approve", "REQUEST_USER_APPROVAL", "Pause for explicit human approval.",
                                Map.of("title", "Approve the previewed file moves",
                                        "explanation", "Review the preview before any file is moved. "
                                                + "GhostShift will never overwrite or delete a file.")),
                        step("move", "MOVE_FILE", "Move the approved files without overwriting.",
                                Map.of("maxFiles", 100))),
                List.of(directoryInput), true, "STOP");

        WorkflowDefinition logTriage = new WorkflowDefinition(1, "Triage a log file (example)",
                "Finds a .log file in the folder, analyzes it deterministically, and saves a Markdown incident "
                        + "report. AI interpretation is off by default; turn it on in the step after testing.",
                List.of(
                        step("list", "LIST_FILES", "List files so a log can be chosen.",
                                Map.of("recursive", false, "maxFiles", 100)),
                        step("filter", "FILTER_FILES", "Keep only .log files.",
                                Map.of("extensions", List.of(".log"))),
                        step("read", "READ_APPROVED_TEXT", "Read the first matching log inside the folder boundary.",
                                Map.of("maxChars", 200000)),
                        step("analyze", "ANALYZE_LOG", "Group error evidence and save a diagnostic report.",
                                Map.of("useAi", false)),
                        step("report", "GENERATE_REPORT", "Save the workflow's report summary.",
                                Map.of("title", "Log triage summary"))),
                List.of(directoryInput), true, "STOP");

        WorkflowDefinition duplicates = new WorkflowDefinition(1, "Find duplicate files (example)",
                "Hashes files and reports exact duplicates. This is read-only: GhostShift never deletes files.",
                List.of(
                        step("list", "LIST_FILES", "List files recursively.",
                                Map.of("recursive", true, "maxFiles", 500)),
                        step("hash", "HASH_FILE", "Hash the selected files and group exact matches.",
                                Map.of())),
                List.of(directoryInput), true, "STOP");

        return List.of(
                new ExampleWorkflow(tidy.name(), tidy.description(), "FILE_ORGANIZATION", tidy),
                new ExampleWorkflow(logTriage.name(), logTriage.description(), "LOG_ANALYSIS", logTriage),
                new ExampleWorkflow(duplicates.name(), duplicates.description(), "DEVELOPER", duplicates)
        );
    }

    private static WorkflowStep step(String id, String type, String description, Map<String, Object> parameters) {
        return new WorkflowStep(id, type, description, parameters, true);
    }

    private WorkflowDefinition parseDraft(String raw) {
        Map<String, Object> root;
        try {
            root = json.readObject(raw);
        } catch (RuntimeException e) {
            throw ApiException.unprocessable("The model returned invalid JSON. Nothing was saved.");
        }
        String name = string(root.get("name"), "Untitled draft");
        String description = string(root.get("description"), "");
        String failurePolicy = string(root.get("failurePolicy"), "STOP").toUpperCase(Locale.ROOT);
        if (!failurePolicy.equals("STOP") && !failurePolicy.equals("CONTINUE_SAFE")) {
            failurePolicy = "STOP";
        }
        List<WorkflowStep> steps = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        Object rawSteps = root.get("steps");
        if (rawSteps instanceof List<?> list) {
            int index = 0;
            for (Object item : list) {
                index++;
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String type = string(map.get("type"), "").toUpperCase(Locale.ROOT);
                if (!registry.exists(type)) {
                    throw ApiException.unprocessable("The model proposed an unsupported action type: "
                            + Text.truncate(type, 60) + ". Nothing was saved.");
                }
                String id = Text.slug(string(map.get("id"), "step-" + index)).replace('-', '-');
                if (id.isBlank() || ids.contains(id)) {
                    id = "step-" + index;
                }
                ids.add(id);
                String stepDescription = string(map.get("description"),
                        registry.require(type).label());
                Map<String, Object> parameters = new LinkedHashMap<>();
                if (map.get("parameters") instanceof Map<?, ?> supplied) {
                    supplied.forEach((key, value) -> {
                        if (key != null) {
                            parameters.put(String.valueOf(key), value);
                        }
                    });
                }
                boolean enabled = !Boolean.FALSE.equals(map.get("enabled"));
                steps.add(new WorkflowStep(id, type, stepDescription, parameters, enabled));
            }
        }
        List<WorkflowInput> inputs = new ArrayList<>();
        Object rawInputs = root.get("inputs");
        if (rawInputs instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String inputName = string(map.get("name"), "");
                String type = string(map.get("type"), "TEXT").toUpperCase(Locale.ROOT);
                if (inputName.isBlank() || !Set.of("DIRECTORY", "TEXT", "NUMBER", "BOOLEAN").contains(type)) {
                    continue;
                }
                inputs.add(new WorkflowInput(inputName, string(map.get("label"), inputName), type,
                        !Boolean.FALSE.equals(map.get("required")), string(map.get("description"), "")));
            }
        }
        WorkflowDefinition definition = new WorkflowDefinition(1,
                Text.truncate(name, 120), Text.truncate(description, 2000), steps, inputs,
                true, failurePolicy);
        return ensureDirectoryInputIfNeeded(definition);
    }

    private WorkflowDefinition ensureDirectoryInputIfNeeded(WorkflowDefinition definition) {
        boolean needsDirectory = definition.steps().stream()
                .filter(WorkflowStep::enabled)
                .map(WorkflowStep::type)
                .anyMatch(type -> DIRECTORY_ACTIONS.contains(type.toUpperCase(Locale.ROOT)));
        if (!needsDirectory) {
            return definition;
        }
        return ensureDirectoryInput(definition);
    }

    private WorkflowDefinition ensureDirectoryInput(WorkflowDefinition definition) {
        boolean present = definition.inputs() != null && definition.inputs().stream()
                .anyMatch(input -> "directoryId".equals(input.name()));
        if (present) {
            return definition;
        }
        List<WorkflowInput> inputs = new ArrayList<>(definition.inputs() == null ? List.of() : definition.inputs());
        inputs.add(new WorkflowInput("directoryId", "Folder", "DIRECTORY", true,
                "The registered folder this workflow may use."));
        return new WorkflowDefinition(definition.schemaVersion(), definition.name(), definition.description(),
                definition.steps(), inputs, true, definition.failurePolicy());
    }

    private Normalized normalize(WorkflowRequest request) {
        WorkflowDefinition definition = request.definition();
        String name = request.name() == null || request.name().isBlank()
                ? definition.name() : request.name().strip();
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Give this workflow a name.");
        }
        String description = request.description() == null ? definition.description() : request.description();
        String category = normalizeCategory(request.category());
        List<WorkflowStep> steps = definition.steps() == null ? List.of() : definition.steps().stream()
                .map(step -> step == null ? null : new WorkflowStep(
                        step.id(),
                        step.type() == null ? "" : step.type().strip().toUpperCase(Locale.ROOT),
                        step.description(),
                        step.parameters() == null ? Map.of() : new LinkedHashMap<>(step.parameters()),
                        step.enabled()))
                .toList();
        WorkflowDefinition normalized = new WorkflowDefinition(1, Text.truncate(name, 120),
                Text.truncate(description, 2000), steps,
                definition.inputs() == null ? List.of() : definition.inputs(), true,
                definition.failurePolicy() == null ? "STOP" : definition.failurePolicy().toUpperCase(Locale.ROOT));
        return new Normalized(normalized.name(), normalized.description(), category, normalized);
    }

    private static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return "GENERAL";
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return CATEGORIES.contains(normalized) ? normalized : "GENERAL";
    }

    private WorkflowRow requireRow(String id) {
        return db.queryOne("""
                        SELECT id, name, description, category, current_version, status, archived, created_at, updated_at
                        FROM workflow WHERE id = ?
                        """, this::mapRow, id)
                .orElseThrow(() -> ApiException.notFound("That workflow does not exist."));
    }

    private DefinitionView definitionView(WorkflowRow row) {
        return db.queryOne("""
                        SELECT definition_json, validation_json FROM workflow_version
                        WHERE workflow_id = ? AND version = ?
                        """, rs -> {
                    WorkflowDefinition definition;
                    try {
                        definition = json.read(rs.getString("definition_json"), WorkflowDefinition.class);
                    } catch (RuntimeException e) {
                        definition = new WorkflowDefinition(1, row.name(), row.description(), List.of(),
                                List.of(), true, "STOP");
                    }
                    WorkflowValidation validation;
                    try {
                        validation = rs.getString("validation_json") == null ? validator.validate(definition)
                                : json.read(rs.getString("validation_json"), WorkflowValidation.class);
                    } catch (RuntimeException e) {
                        validation = validator.validate(definition);
                    }
                    return new DefinitionView(definition, validation);
                }, row.id(), row.currentVersion())
                .orElseGet(() -> new DefinitionView(
                        new WorkflowDefinition(1, row.name(), row.description(), List.of(), List.of(), true, "STOP"),
                        new WorkflowValidation(false, List.of("This workflow has no stored definition."),
                                List.of(), List.of(), List.of())));
    }

    private List<WorkflowVersionView> versions(String workflowId) {
        return db.query("""
                        SELECT id, version, schema_version, definition_json, validation_json, created_at
                        FROM workflow_version WHERE workflow_id = ? ORDER BY version DESC
                        """, rs -> {
                    WorkflowDefinition definition = null;
                    try {
                        definition = json.read(rs.getString("definition_json"), WorkflowDefinition.class);
                    } catch (RuntimeException ignored) {
                    }
                    WorkflowValidation validation;
                    try {
                        validation = rs.getString("validation_json") == null || definition == null
                                ? validator.validate(definition)
                                : json.read(rs.getString("validation_json"), WorkflowValidation.class);
                    } catch (RuntimeException e) {
                        validation = validator.validate(definition);
                    }
                    return new WorkflowVersionView(rs.getString("id"), rs.getInt("version"),
                            rs.getInt("schema_version"), rs.getString("created_at"), validation);
                }, workflowId);
    }

    private WorkflowRow mapRow(ResultSet rs) throws SQLException {
        return new WorkflowRow(rs.getString("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("category"), rs.getInt("current_version"), rs.getString("status"),
                rs.getInt("archived") == 1, rs.getString("created_at"), rs.getString("updated_at"));
    }

    private static String string(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text.strip() : fallback;
    }

    private record WorkflowRow(String id, String name, String description, String category,
                               int currentVersion, String status, boolean archived,
                               String createdAt, String updatedAt) {
    }

    private record DefinitionView(WorkflowDefinition definition, WorkflowValidation validation) {
    }

    private record Normalized(String name, String description, String category, WorkflowDefinition definition) {
    }

    public record ExampleWorkflow(String name, String description, String category,
                                  WorkflowDefinition definition) {
    }
}
