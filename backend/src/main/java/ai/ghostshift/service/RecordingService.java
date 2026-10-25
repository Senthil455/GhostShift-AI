package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.RecordedStepRequest;
import ai.ghostshift.domain.RecordedStepView;
import ai.ghostshift.domain.RecordingView;
import ai.ghostshift.domain.SaveRecordingRequest;
import ai.ghostshift.domain.StartRecordingRequest;
import ai.ghostshift.domain.UpdateRecordedStepRequest;
import ai.ghostshift.domain.WorkflowDefinition;
import ai.ghostshift.domain.WorkflowDetail;
import ai.ghostshift.domain.WorkflowInput;
import ai.ghostshift.domain.WorkflowRequest;
import ai.ghostshift.domain.WorkflowStep;
import ai.ghostshift.domain.WorkflowValidation;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.Text;
import ai.ghostshift.workflow.ActionRegistry;
import ai.ghostshift.workflow.WorkflowValidator;
import jakarta.inject.Singleton;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Records an allowlisted sequence of file actions and turns it into a draft workflow.
 * Browser-screen capture is deliberately not implemented in this version; claiming
 * otherwise would be dishonest. File-action recording is real and replayable.
 */
@Singleton
public class RecordingService {
    private static final Set<String> MUTABLE = Set.of("ACTIVE", "PAUSED", "STOPPED");
    private static final Set<String> DIRECTORY_ACTIONS = Set.of(
            "LIST_FILES", "READ_APPROVED_TEXT", "ANALYZE_LOG", "MOVE_FILE");

    private final Database db;
    private final Json json;
    private final ActionRegistry registry;
    private final WorkflowValidator validator;
    private final WorkflowService workflows;
    private final DirectoryService directories;
    private final ActivityService activity;

    public RecordingService(Database db, Json json, ActionRegistry registry, WorkflowValidator validator,
                            WorkflowService workflows, DirectoryService directories,
                            ActivityService activity) {
        this.db = db;
        this.json = json;
        this.registry = registry;
        this.validator = validator;
        this.workflows = workflows;
        this.directories = directories;
        this.activity = activity;
    }

    public List<RecordingView> list() {
        return db.query("""
                        SELECT id, name, mode, status, directory_id, created_at, updated_at, stopped_at, workflow_id
                        FROM recording ORDER BY created_at DESC
                        """, this::mapRecording).stream().map(row -> withSteps(row)).toList();
    }

    public RecordingView get(String id) {
        return withSteps(require(id));
    }

    public RecordingView start(StartRecordingRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw ApiException.badRequest("Give this recording a name.");
        }
        if (request.name().length() > 120) {
            throw ApiException.badRequest("Keep the recording name under 120 characters.");
        }
        String mode = request.mode() == null ? "FILE" : request.mode().strip().toUpperCase(Locale.ROOT);
        if (!mode.equals("FILE")) {
            throw ApiException.unprocessable("Browser and screen recording are not available in this version. "
                    + "This recording mode is FILE: log allowlisted file actions, then save them as a draft workflow.");
        }
        if (request.directoryId() != null && request.directoryId() > 0) {
            directories.get(request.directoryId());
        }
        String id = UUID.randomUUID().toString();
        String now = Text.now();
        db.update("""
                        INSERT INTO recording
                            (id, name, mode, status, directory_id, created_at, updated_at)
                        VALUES (?, ?, 'FILE', 'ACTIVE', ?, ?, ?)
                        """, id, Text.truncate(request.name(), 120),
                request.directoryId() == null || request.directoryId() <= 0 ? null : request.directoryId(),
                now, now);
        activity.record("RECORDING", "Recording started", request.name() + " · file actions", id);
        return get(id);
    }

    public RecordingView pause(String id) {
        RecordingRow row = require(id);
        if (!row.status().equals("ACTIVE")) {
            throw ApiException.conflict("Only an active recording can be paused.");
        }
        db.update("UPDATE recording SET status = 'PAUSED', updated_at = ? WHERE id = ?", Text.now(), id);
        return get(id);
    }

    public RecordingView resume(String id) {
        RecordingRow row = require(id);
        if (!row.status().equals("PAUSED")) {
            throw ApiException.conflict("Only a paused recording can be resumed.");
        }
        db.update("UPDATE recording SET status = 'ACTIVE', updated_at = ? WHERE id = ?", Text.now(), id);
        return get(id);
    }

    public RecordingView stop(String id) {
        RecordingRow row = require(id);
        if (row.status().equals("SAVED")) {
            throw ApiException.conflict("This recording has already been saved as a workflow.");
        }
        db.update("""
                UPDATE recording SET status = 'STOPPED', stopped_at = COALESCE(stopped_at, ?), updated_at = ?
                WHERE id = ?
                """, Text.now(), Text.now(), id);
        activity.record("RECORDING", "Recording stopped",
                row.name() + " · review the steps before saving", id);
        return get(id);
    }

    public RecordingView addStep(String recordingId, RecordedStepRequest request) {
        RecordingRow row = requireMutable(recordingId);
        if (request == null || request.actionType() == null) {
            throw ApiException.badRequest("Choose an action type for this recorded step.");
        }
        String type = request.actionType().strip().toUpperCase(Locale.ROOT);
        if (!registry.exists(type)) {
            throw ApiException.badRequest("That action type is not in the allowlisted action catalog.");
        }
        if (row.directoryId() == null && DIRECTORY_ACTIONS.contains(type)) {
            throw ApiException.badRequest("Start a new recording with a folder selected before recording "
                    + type + ". This step was not added.");
        }
        List<String> errors = registry.validateParameters(type, request.parameters());
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("The recorded step has invalid parameters.",
                    Map.of("errors", errors));
        }
        var descriptor = registry.require(type);
        String description = request.description() == null || request.description().isBlank()
                ? descriptor.label() : Text.truncate(request.description(), 300);
        int nextOrder = db.queryOne("SELECT COALESCE(MAX(step_order), 0) + 1 FROM recorded_step WHERE recording_id = ?",
                rs -> rs.getInt(1), recordingId).orElse(1);
        db.update("""
                        INSERT INTO recorded_step
                            (id, recording_id, step_order, action_type, description, parameters_json,
                             requires_approval, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID().toString(), recordingId, nextOrder, type, description,
                json.write(request.parameters() == null ? Map.of() : request.parameters()),
                descriptor.requiresApproval() ? 1 : 0, Text.now());
        touch(recordingId);
        return get(recordingId);
    }

    public RecordingView updateStep(String recordingId, String stepId, UpdateRecordedStepRequest request) {
        requireMutable(recordingId);
        StepRow step = steps(recordingId).stream().filter(item -> item.id().equals(stepId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("That recorded step does not exist."));
        if (request == null) {
            throw ApiException.badRequest("A step update is required.");
        }
        Map<String, Object> parameters = request.parameters() == null ? step.parameters() : request.parameters();
        List<String> errors = registry.validateParameters(step.actionType(), parameters);
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("The recorded step has invalid parameters.",
                    Map.of("errors", errors));
        }
        String description = request.description() == null ? step.description()
                : Text.truncate(request.description(), 300);
        int order = request.order() == null ? step.order() : Math.clamp(request.order(), 1, 500);
        db.update("""
                        UPDATE recorded_step SET description = ?, step_order = ?, parameters_json = ?
                        WHERE recording_id = ? AND id = ?
                        """, description, order, json.write(parameters), recordingId, stepId);
        normalizeOrder(recordingId);
        touch(recordingId);
        return get(recordingId);
    }

    public RecordingView deleteStep(String recordingId, String stepId) {
        requireMutable(recordingId);
        int changed = db.update("DELETE FROM recorded_step WHERE recording_id = ? AND id = ?",
                recordingId, stepId);
        if (changed == 0) {
            throw ApiException.notFound("That recorded step does not exist.");
        }
        normalizeOrder(recordingId);
        touch(recordingId);
        return get(recordingId);
    }

    public void delete(String recordingId) {
        require(recordingId);
        db.update("DELETE FROM recording WHERE id = ?", recordingId);
        activity.record("RECORDING", "Recording deleted", "Recorded steps removed; any saved workflow is preserved.",
                recordingId);
    }

    public WorkflowDetail saveAsWorkflow(String recordingId, SaveRecordingRequest request) {
        RecordingRow row = require(recordingId);
        if (row.status().equals("SAVED") && row.workflowId() != null) {
            throw ApiException.conflict("This recording was already saved as a workflow.");
        }
        List<StepRow> recorded = steps(recordingId);
        if (recorded.isEmpty()) {
            throw ApiException.unprocessable("Add at least one recorded step before saving a workflow.");
        }
        List<WorkflowStep> steps = new ArrayList<>();
        int index = 0;
        for (StepRow step : recorded) {
            index++;
            steps.add(new WorkflowStep("recorded-" + index, step.actionType(), step.description(),
                    step.parameters(), true));
        }
        boolean needsDirectory = recorded.stream().anyMatch(step -> DIRECTORY_ACTIONS.contains(step.actionType()));
        List<WorkflowInput> inputs = new ArrayList<>();
        Long directoryId = row.directoryId();
        if (needsDirectory || directoryId != null) {
            inputs.add(new WorkflowInput("directoryId", "Folder", "DIRECTORY", true,
                    "The registered folder this recorded workflow may use."));
        }
        String name = request != null && request.name() != null && !request.name().isBlank()
                ? request.name() : row.name();
        String description = request == null || request.description() == null
                ? "Created from a GhostShift file-action recording. Review every step before approving."
                : request.description();
        String category = request == null || request.category() == null ? "CUSTOM" : request.category();
        WorkflowDefinition definition = new WorkflowDefinition(1, Text.truncate(name, 120),
                Text.truncate(description, 2000), steps, inputs, true, "STOP");
        WorkflowValidation validation = validator.validate(definition);
        if (!validation.valid()) {
            throw ApiException.unprocessable("The recording cannot be saved yet. "
                    + String.join(" ", validation.errors()), Map.of("errors", validation.errors()));
        }
        WorkflowDetail workflow = workflows.create(new WorkflowRequest(name, description, category, definition));
        db.update("""
                UPDATE recording SET status = 'SAVED', workflow_id = ?, updated_at = ?, stopped_at = COALESCE(stopped_at, ?)
                WHERE id = ?
                """, workflow.id(), Text.now(), Text.now(), recordingId);
        activity.record("RECORDING", "Recording saved as workflow",
                row.name() + " · review and approve it before running", workflow.id());
        return workflow;
    }

    private RecordingView withSteps(RecordingRow row) {
        List<RecordedStepView> steps = steps(row.id()).stream()
                .map(step -> new RecordedStepView(step.id(), step.order(), step.actionType(),
                        step.description(), step.parameters(), step.requiresApproval()))
                .toList();
        return new RecordingView(row.id(), row.name(), row.mode(), row.status(), row.directoryId(),
                row.createdAt(), row.updatedAt(), row.stoppedAt(), row.workflowId(), steps);
    }

    private List<StepRow> steps(String recordingId) {
        return db.query("""
                        SELECT id, step_order, action_type, description, parameters_json, requires_approval
                        FROM recorded_step WHERE recording_id = ? ORDER BY step_order, created_at
                        """, rs -> new StepRow(rs.getString("id"), rs.getInt("step_order"),
                        rs.getString("action_type"), rs.getString("description"),
                        json.toObject(rs.getString("parameters_json")),
                        rs.getInt("requires_approval") == 1), recordingId);
    }

    private void normalizeOrder(String recordingId) {
        List<String> ids = db.query("""
                SELECT id FROM recorded_step WHERE recording_id = ? ORDER BY step_order, created_at
                """, rs -> rs.getString(1), recordingId);
        int order = 0;
        for (String id : ids) {
            order++;
            db.update("UPDATE recorded_step SET step_order = ? WHERE id = ?", order, id);
        }
    }

    private void touch(String recordingId) {
        db.update("UPDATE recording SET updated_at = ? WHERE id = ?", Text.now(), recordingId);
    }

    private RecordingRow requireMutable(String id) {
        RecordingRow row = require(id);
        if (!MUTABLE.contains(row.status())) {
            throw ApiException.conflict("This recording is saved. Start a new recording to change steps.");
        }
        return row;
    }

    private RecordingRow require(String id) {
        return db.queryOne("""
                        SELECT id, name, mode, status, directory_id, created_at, updated_at, stopped_at, workflow_id
                        FROM recording WHERE id = ?
                        """, this::mapRecording, id)
                .orElseThrow(() -> ApiException.notFound("That recording does not exist."));
    }

    private RecordingRow mapRecording(ResultSet rs) throws SQLException {
        Object directoryValue = rs.getObject("directory_id");
        Long directoryId = directoryValue instanceof Number number ? number.longValue() : null;
        return new RecordingRow(rs.getString("id"), rs.getString("name"), rs.getString("mode"),
                rs.getString("status"), directoryId, rs.getString("created_at"), rs.getString("updated_at"),
                rs.getString("stopped_at"), rs.getString("workflow_id"));
    }

    private record RecordingRow(String id, String name, String mode, String status, Long directoryId,
                                String createdAt, String updatedAt, String stoppedAt, String workflowId) {
    }

    private record StepRow(String id, int order, String actionType, String description,
                           Map<String, Object> parameters, boolean requiresApproval) {
    }
}
