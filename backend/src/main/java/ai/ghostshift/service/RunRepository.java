package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.ActionExecution;
import ai.ghostshift.domain.Approval;
import ai.ghostshift.domain.RunEvent;
import ai.ghostshift.domain.RunSummary;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class RunRepository {
    private final Database db;
    private final Json json;

    public RunRepository(Database db, Json json) {
        this.db = db;
        this.json = json;
    }

    public void createRun(String id, String workflowId, String workflowName, int workflowVersion,
                          boolean dryRun, String inputsJson) {
        db.update("""
                        INSERT INTO workflow_run
                            (id, workflow_id, workflow_name, workflow_version, status, dry_run, trigger_type,
                             inputs_json, context_json, cancel_requested, current_step)
                        VALUES (?, ?, ?, ?, 'QUEUED', ?, 'MANUAL', ?, '{}', 0, 0)
                        """, id, workflowId, workflowName, workflowVersion, dryRun ? 1 : 0, inputsJson);
    }

    public void createAction(String runId, String stepId, int stepIndex, String actionType,
                             Map<String, Object> parameters) {
        db.update("""
                        INSERT INTO action_execution
                            (id, run_id, step_id, step_index, action_type, status, parameters_json)
                        VALUES (?, ?, ?, ?, ?, 'PENDING', ?)
                        """, UUID.randomUUID().toString(), runId, stepId, stepIndex, actionType,
                json.write(parameters == null ? Map.of() : parameters));
    }

    public WorkflowRun get(String runId) {
        RunRow row = db.queryOne("""
                        SELECT id, workflow_id, workflow_name, workflow_version, status, dry_run, trigger_type,
                               inputs_json, context_json, summary, cancel_requested, current_step,
                               started_at, ended_at, duration_ms
                        FROM workflow_run WHERE id = ?
                        """, this::mapRun, runId)
                .orElseThrow(() -> ApiException.notFound("That workflow run does not exist."));
        return new WorkflowRun(row.id(), row.workflowId(), row.workflowName(), row.workflowVersion(),
                row.status(), row.dryRun(), row.triggerType(), row.inputs(), row.summary(),
                row.cancelRequested(), row.currentStep(), row.startedAt(), row.endedAt(), row.durationMs(),
                actions(runId), approvals(runId));
    }

    public RunRow getRow(String runId) {
        return db.queryOne("""
                        SELECT id, workflow_id, workflow_name, workflow_version, status, dry_run, trigger_type,
                               inputs_json, context_json, summary, cancel_requested, current_step,
                               started_at, ended_at, duration_ms
                        FROM workflow_run WHERE id = ?
                        """, this::mapRun, runId)
                .orElseThrow(() -> ApiException.notFound("That workflow run does not exist."));
    }

    public String contextJson(String runId) {
        return db.queryOne("SELECT context_json FROM workflow_run WHERE id = ?",
                rs -> rs.getString(1), runId).orElse("{}");
    }

    public void saveContext(String runId, String contextJson) {
        db.update("UPDATE workflow_run SET context_json = ? WHERE id = ?", contextJson, runId);
    }

    public List<ActionExecution> actions(String runId) {
        return db.query("""
                        SELECT id, step_id, step_index, action_type, status, parameters_json, result_json,
                               error, started_at, ended_at
                        FROM action_execution WHERE run_id = ? ORDER BY step_index
                        """, rs -> new ActionExecution(
                        rs.getString("id"), rs.getString("step_id"), rs.getInt("step_index"),
                        rs.getString("action_type"), rs.getString("status"),
                        mapJson(rs.getString("parameters_json")), mapJson(rs.getString("result_json")),
                        rs.getString("error"), rs.getString("started_at"), rs.getString("ended_at")), runId);
    }

    public ActionExecution action(String runId, String stepId) {
        List<ActionExecution> actions = db.query("""
                        SELECT id, step_id, step_index, action_type, status, parameters_json, result_json,
                               error, started_at, ended_at
                        FROM action_execution WHERE run_id = ? AND step_id = ?
                        """, rs -> new ActionExecution(
                        rs.getString("id"), rs.getString("step_id"), rs.getInt("step_index"),
                        rs.getString("action_type"), rs.getString("status"),
                        mapJson(rs.getString("parameters_json")), mapJson(rs.getString("result_json")),
                        rs.getString("error"), rs.getString("started_at"), rs.getString("ended_at")),
                runId, stepId);
        return actions.isEmpty() ? null : actions.getFirst();
    }

    public void updateAction(String actionId, String status, Map<String, Object> result, String error,
                             String startedAt, String endedAt) {
        db.update("""
                        UPDATE action_execution
                        SET status = ?, result_json = COALESCE(?, result_json), error = ?,
                            started_at = COALESCE(?, started_at), ended_at = ?
                        WHERE id = ?
                        """, status, result == null ? null : json.write(result), error,
                startedAt, endedAt, actionId);
    }

    public void markActionStarted(String actionId) {
        db.update("UPDATE action_execution SET status = 'RUNNING', started_at = COALESCE(started_at, ?) WHERE id = ?",
                Text.now(), actionId);
    }

    public void markActionWaiting(String actionId) {
        db.update("UPDATE action_execution SET status = 'WAITING_FOR_APPROVAL' WHERE id = ?", actionId);
    }

    public void markRemainingCancelled(String runId) {
        db.update("""
                UPDATE action_execution SET status = 'CANCELLED', ended_at = ?
                WHERE run_id = ? AND status IN ('PENDING', 'WAITING_FOR_APPROVAL', 'RUNNING')
                """, Text.now(), runId);
    }

    public void markRemainingSkipped(String runId) {
        db.update("""
                UPDATE action_execution SET status = 'SKIPPED', ended_at = ?
                WHERE run_id = ? AND status IN ('PENDING', 'WAITING_FOR_APPROVAL', 'RUNNING')
                """, Text.now(), runId);
    }

    /** Only one caller may move a waiting run back to running. */
    public boolean claimResume(String runId) {
        return db.update("""
                UPDATE workflow_run SET status = 'RUNNING'
                WHERE id = ? AND status = 'WAITING_FOR_APPROVAL'
                """, runId) == 1;
    }

    public List<RunSummary> listSummaries(int limit) {
        int bounded = Math.clamp(limit, 1, 100);
        return db.query("""
                        SELECT r.id, r.workflow_id, r.workflow_name, r.status, r.dry_run, r.started_at,
                               r.ended_at, r.duration_ms, r.summary,
                               (SELECT COUNT(*) FROM action_execution a WHERE a.run_id = r.id) AS total_steps,
                               (SELECT COUNT(*) FROM action_execution a WHERE a.run_id = r.id AND a.status = 'SUCCEEDED') AS succeeded,
                               (SELECT COUNT(*) FROM action_execution a WHERE a.run_id = r.id AND a.status = 'FAILED') AS failed,
                               (SELECT COUNT(*) FROM action_execution a WHERE a.run_id = r.id AND a.status = 'WAITING_FOR_APPROVAL') AS waiting
                        FROM workflow_run r
                        ORDER BY COALESCE(r.started_at, '') DESC, r.rowid DESC
                        LIMIT ?
                        """, RunRepository::mapSummary, bounded);
    }

    public long count(String where, Object... params) {
        return db.queryOne("SELECT COUNT(*) FROM workflow_run " + where, rs -> rs.getLong(1), params).orElse(0L);
    }

    public void markStarted(String runId) {
        db.update("""
                UPDATE workflow_run SET status = 'RUNNING', started_at = COALESCE(started_at, ?) WHERE id = ?
                """, Text.now(), runId);
    }

    public void markWaiting(String runId, int currentStep) {
        db.update("UPDATE workflow_run SET status = 'WAITING_FOR_APPROVAL', current_step = ? WHERE id = ?",
                currentStep, runId);
    }

    public void markRunning(String runId, int currentStep) {
        db.update("UPDATE workflow_run SET status = 'RUNNING', current_step = ? WHERE id = ?",
                currentStep, runId);
    }

    public void setCurrentStep(String runId, int currentStep) {
        db.update("UPDATE workflow_run SET current_step = ? WHERE id = ?", currentStep, runId);
    }

    public void finalizeRun(String runId, String status, String summary, long durationMs) {
        db.update("""
                        UPDATE workflow_run
                        SET status = ?, summary = ?, ended_at = ?, duration_ms = ?, cancel_requested = 0
                        WHERE id = ?
                        """, status, Text.truncate(summary, 2000), Text.now(), durationMs, runId);
    }

    public void markQueuedRunInterrupted(String runId) {
        long duration = duration(runId);
        db.update("""
                        UPDATE workflow_run SET status = 'FAILED', summary = ?, ended_at = ?, duration_ms = ?
                        WHERE id = ?
                        """, "GhostShift restarted while this run was executing. No further actions will run.",
                Text.now(), duration, runId);
        db.update("""
                UPDATE action_execution SET status = 'FAILED', ended_at = ?,
                    error = 'Interrupted by an application restart.'
                WHERE run_id = ? AND status IN ('RUNNING')
                """, Text.now(), runId);
    }

    /** Queued or running work cannot survive a process restart; waiting runs can be resumed. */
    public int recoverInterruptedRuns() {
        List<String> ids = db.query("""
                SELECT id FROM workflow_run WHERE status IN ('QUEUED', 'RUNNING')
                """, rs -> rs.getString(1));
        for (String id : ids) {
            markQueuedRunInterrupted(id);
            expireApprovals(id);
        }
        return ids.size();
    }

    public long duration(String runId) {
        String started = db.queryOne("SELECT started_at FROM workflow_run WHERE id = ?",
                rs -> rs.getString(1), runId).orElse(null);
        if (started == null) {
            return 0L;
        }
        try {
            return Math.max(0L, java.time.Duration.between(
                    java.time.Instant.parse(started), java.time.Instant.now()).toMillis());
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    public void requestCancel(String runId) {
        db.update("UPDATE workflow_run SET cancel_requested = 1 WHERE id = ?", runId);
    }

    public void clearCancel(String runId) {
        db.update("UPDATE workflow_run SET cancel_requested = 0 WHERE id = ?", runId);
    }

    public boolean isCancelRequested(String runId) {
        return db.queryOne("SELECT cancel_requested FROM workflow_run WHERE id = ?",
                rs -> rs.getInt(1) == 1, runId).orElse(false);
    }

    public List<Approval> approvals(String runId) {
        return db.query("""
                        SELECT a.id, a.run_id, a.action_execution_id, r.workflow_name, a.kind, a.title,
                               a.explanation, a.risk, a.status, a.resolution_note, a.created_at, a.resolved_at
                        FROM approval_request a
                        JOIN workflow_run r ON r.id = a.run_id
                        WHERE a.run_id = ?
                        ORDER BY a.created_at DESC
                        """, this::mapApproval, runId);
    }

    public List<Approval> pendingApprovals() {
        return db.query("""
                        SELECT a.id, a.run_id, a.action_execution_id, r.workflow_name, a.kind, a.title,
                               a.explanation, a.risk, a.status, a.resolution_note, a.created_at, a.resolved_at
                        FROM approval_request a
                        JOIN workflow_run r ON r.id = a.run_id
                        WHERE a.status = 'PENDING'
                        ORDER BY a.created_at DESC
                        """, this::mapApproval);
    }

    public Approval approval(String approvalId) {
        return db.queryOne("""
                        SELECT a.id, a.run_id, a.action_execution_id, r.workflow_name, a.kind, a.title,
                               a.explanation, a.risk, a.status, a.resolution_note, a.created_at, a.resolved_at
                        FROM approval_request a
                        JOIN workflow_run r ON r.id = a.run_id
                        WHERE a.id = ?
                        """, this::mapApproval, approvalId)
                .orElseThrow(() -> ApiException.notFound("That approval request does not exist."));
    }

    public Approval approvalForAction(String runId, String actionExecutionId) {
        List<Approval> approvals = db.query("""
                        SELECT a.id, a.run_id, a.action_execution_id, r.workflow_name, a.kind, a.title,
                               a.explanation, a.risk, a.status, a.resolution_note, a.created_at, a.resolved_at
                        FROM approval_request a
                        JOIN workflow_run r ON r.id = a.run_id
                        WHERE a.run_id = ? AND a.action_execution_id = ?
                        ORDER BY a.created_at DESC LIMIT 1
                        """, this::mapApproval, runId, actionExecutionId);
        return approvals.isEmpty() ? null : approvals.getFirst();
    }

    public Approval createApproval(String runId, String actionExecutionId, String kind,
                                   String title, String explanation, String risk) {
        String id = UUID.randomUUID().toString();
        db.update("""
                        INSERT INTO approval_request
                            (id, run_id, action_execution_id, kind, title, explanation, risk, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                        """, id, runId, actionExecutionId, kind, Text.truncate(title, 300),
                Text.truncate(explanation, 2000), risk, Text.now());
        return approval(id);
    }

    public int resolveApproval(String approvalId, String status, String note) {
        return db.update("""
                UPDATE approval_request SET status = ?, resolution_note = ?, resolved_at = ?
                WHERE id = ? AND status = 'PENDING'
                """, status, Text.truncate(note, 500), Text.now(), approvalId);
    }

    public void expireApprovals(String runId) {
        db.update("""
                UPDATE approval_request SET status = 'EXPIRED', resolved_at = ?
                WHERE run_id = ? AND status = 'PENDING'
                """, Text.now(), runId);
    }

    public void insertFileOperation(String id, String runId, String planId, String originalPath,
                                    String destinationPath, String recoveryJson) {
        db.update("""
                        INSERT INTO file_operation
                            (id, run_id, plan_id, original_path, destination_path, operation_type, status,
                             recovery_json, created_at)
                        VALUES (?, ?, ?, ?, ?, 'MOVE', 'COMPLETED', ?, ?)
                        """, id, runId, planId, originalPath, destinationPath, recoveryJson, Text.now());
    }

    public void addEvent(String runId, String level, String message, Map<String, Object> data) {
        db.update("""
                INSERT INTO run_event(run_id, at, level, message, data_json) VALUES (?, ?, ?, ?, ?)
                """, runId, Text.now(), level, Text.truncate(message, 1000), data == null ? null : json.write(data));
    }

    public List<RunEvent> events(String runId, long afterId) {
        return db.query("""
                        SELECT id, run_id, at, level, message, data_json
                        FROM run_event WHERE run_id = ? AND id > ? ORDER BY id
                        """, rs -> new RunEvent(rs.getLong("id"), rs.getString("run_id"), rs.getString("at"),
                        rs.getString("level"), rs.getString("message"), mapJson(rs.getString("data_json"))),
                runId, Math.max(0, afterId));
    }

    private RunRow mapRun(ResultSet rs) throws SQLException {
        return new RunRow(
                rs.getString("id"), rs.getString("workflow_id"), rs.getString("workflow_name"),
                rs.getInt("workflow_version"), rs.getString("status"), rs.getInt("dry_run") == 1,
                rs.getString("trigger_type"), mapJson(rs.getString("inputs_json")),
                mapJson(rs.getString("context_json")), rs.getString("summary"),
                rs.getInt("cancel_requested") == 1, rs.getInt("current_step"),
                rs.getString("started_at"), rs.getString("ended_at"),
                rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms")
        );
    }

    private Approval mapApproval(ResultSet rs) throws SQLException {
        return new Approval(
                rs.getString("id"), rs.getString("run_id"), rs.getString("action_execution_id"),
                rs.getString("workflow_name"), rs.getString("kind"), rs.getString("title"),
                rs.getString("explanation"), rs.getString("risk"), rs.getString("status"),
                rs.getString("resolution_note"), rs.getString("created_at"), rs.getString("resolved_at")
        );
    }

    private static RunSummary mapSummary(ResultSet rs) throws SQLException {
        return new RunSummary(
                rs.getString("id"), rs.getString("workflow_id"), rs.getString("workflow_name"),
                rs.getString("status"), rs.getInt("dry_run") == 1, rs.getString("started_at"),
                rs.getString("ended_at"), rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms"),
                rs.getInt("total_steps"), rs.getInt("succeeded"), rs.getInt("failed"),
                rs.getInt("waiting"), rs.getString("summary")
        );
    }

    private Map<String, Object> mapJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return json.readObject(value);
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    public record RunRow(
            String id,
            String workflowId,
            String workflowName,
            int workflowVersion,
            String status,
            boolean dryRun,
            String triggerType,
            Map<String, Object> inputs,
            Map<String, Object> context,
            String summary,
            boolean cancelRequested,
            int currentStep,
            String startedAt,
            String endedAt,
            Long durationMs
    ) {
    }
}
