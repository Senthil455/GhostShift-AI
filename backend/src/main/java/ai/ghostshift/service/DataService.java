package ai.ghostshift.service;

import ai.ghostshift.ai.GhostBrainService;
import ai.ghostshift.api.ApiException;
import ai.ghostshift.config.AppConfig;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.ActivityItem;
import ai.ghostshift.domain.AiStatus;
import ai.ghostshift.domain.DataExport;
import ai.ghostshift.domain.DeleteDataRequest;
import ai.ghostshift.domain.HealthStatus;
import ai.ghostshift.domain.ReportSummary;
import ai.ghostshift.domain.RunSummary;
import ai.ghostshift.domain.WorkflowSummary;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Health, export, and careful local data reset. It never deletes user files. */
@Singleton
public class DataService {
    private static final Logger LOG = LoggerFactory.getLogger(DataService.class);

    private final Database db;
    private final AppConfig config;
    private final GhostBrainService brain;
    private final WorkflowService workflows;
    private final RunRepository runs;
    private final ReportService reports;
    private final ActivityService activity;
    private final SettingsService settings;
    private final Instant startedAt = Instant.now();

    public DataService(Database db, AppConfig config, GhostBrainService brain, WorkflowService workflows,
                       RunRepository runs, ReportService reports, ActivityService activity,
                       SettingsService settings) {
        this.db = db;
        this.config = config;
        this.brain = brain;
        this.workflows = workflows;
        this.runs = runs;
        this.reports = reports;
        this.activity = activity;
        this.settings = settings;
    }

    public HealthStatus health() {
        String database = "OK";
        try {
            db.queryOne("SELECT 1", rs -> rs.getInt(1));
        } catch (RuntimeException e) {
            database = "ERROR";
            LOG.warn("Health check could not reach SQLite: {}", e.getMessage());
        }
        AiStatus ai;
        try {
            ai = brain.status(false);
        } catch (RuntimeException e) {
            ai = AiStatus.unavailable(settings.modelSettings().endpoint(), settings.modelSettings().model(),
                    "The local model status could not be checked.");
        }
        String status = database.equals("OK") ? "ok" : "degraded";
        return new HealthStatus(status, AppConfig.VERSION, database, ai.status(), ai.model(),
                Math.max(0, Duration.between(startedAt, Instant.now()).toSeconds()), Text.now());
    }

    public DataExport export() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("directories", db.query("""
                        SELECT id, display_name, canonical_path, directory_type, allow_read, allow_organize,
                               created_at, last_scan_at
                        FROM registered_directory WHERE active = 1 ORDER BY id
                        """, rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("displayName", rs.getString("display_name"));
                    row.put("path", rs.getString("canonical_path"));
                    row.put("directoryType", rs.getString("directory_type"));
                    row.put("allowRead", rs.getInt("allow_read") == 1);
                    row.put("allowOrganize", rs.getInt("allow_organize") == 1);
                    row.put("createdAt", rs.getString("created_at"));
                    row.put("lastScanAt", rs.getString("last_scan_at"));
                    return row;
                }));
        List<WorkflowSummary> workflowList = workflows.list(true);
        data.put("workflows", workflowList);
        data.put("plans", db.query("""
                        SELECT id, directory_id, status, file_count, conflict_count, summary, created_at, executed_at
                        FROM organization_plan ORDER BY created_at DESC LIMIT 200
                        """, rs -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("directoryId", rs.getLong("directory_id"));
                    row.put("status", rs.getString("status"));
                    row.put("fileCount", rs.getInt("file_count"));
                    row.put("conflictCount", rs.getInt("conflict_count"));
                    row.put("summary", rs.getString("summary"));
                    row.put("createdAt", rs.getString("created_at"));
                    row.put("executedAt", rs.getString("executed_at"));
                    return row;
                }));
        List<RunSummary> runList = runs.listSummaries(200);
        data.put("runs", runList);
        List<ReportSummary> reportList = reports.list(200);
        data.put("reports", reportList);
        data.put("approvals", runs.pendingApprovals());
        data.put("activity", activity.recent(200));
        data.put("settings", settings.settings());
        data.put("notice", "This export contains GhostShift metadata and activity, not user file contents. "
                + "API tokens are never stored. Generated report Markdown is stored separately.");
        return new DataExport(Text.now(), AppConfig.VERSION, data);
    }

    public Map<String, Object> reset(DeleteDataRequest request) {
        if (request == null || request.scope() == null || request.scope().isBlank()) {
            throw ApiException.badRequest("Choose a data scope to clear.");
        }
        if (request.confirmation() == null || !request.confirmation().strip().equals("DELETE")) {
            throw ApiException.badRequest("Type DELETE in the confirmation field to clear local data.");
        }
        String scope = request.scope().strip().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVITY", "RUNS", "REPORTS", "PLANS", "ALL").contains(scope)) {
            throw ApiException.badRequest("Data scope must be ACTIVITY, RUNS, REPORTS, PLANS, or ALL.");
        }
        if (scope.equals("RUNS") || scope.equals("ALL")) {
            long active = db.queryOne("""
                    SELECT COUNT(*) FROM workflow_run WHERE status IN ('RUNNING', 'QUEUED')
                    """, rs -> rs.getLong(1)).orElse(0L);
            if (active > 0) {
                throw ApiException.conflict("Cancel active workflow runs before clearing run history.");
            }
        }
        if (scope.equals("PLANS") || scope.equals("ALL")) {
            long activePlans = db.queryOne("""
                    SELECT COUNT(*) FROM organization_plan WHERE status = 'EXECUTING'
                    """, rs -> rs.getLong(1)).orElse(0L);
            if (activePlans > 0) {
                throw ApiException.conflict("Wait for active organization plans to finish or cancel them first.");
            }
        }
        Map<String, Object> cleared = new LinkedHashMap<>();
        cleared.put("scope", scope);
        if (scope.equals("ACTIVITY") || scope.equals("ALL")) {
            cleared.put("activity", count("activity"));
            db.update("DELETE FROM activity");
        }
        if (scope.equals("RUNS") || scope.equals("ALL")) {
            cleared.put("runs", count("workflow_run"));
            db.update("DELETE FROM file_operation WHERE run_id IS NOT NULL");
            db.update("DELETE FROM run_event");
            db.update("DELETE FROM approval_request");
            db.update("DELETE FROM action_execution");
            db.update("DELETE FROM workflow_run");
        }
        if (scope.equals("REPORTS") || scope.equals("ALL")) {
            cleared.put("reports", count("report"));
            reports.deleteAllReports();
        }
        if (scope.equals("PLANS") || scope.equals("ALL")) {
            cleared.put("plans", count("organization_plan"));
            db.update("DELETE FROM file_operation WHERE plan_id IS NOT NULL");
            db.update("DELETE FROM organization_action");
            db.update("DELETE FROM organization_plan");
        }
        if (scope.equals("ALL")) {
            cleared.put("workflows", count("workflow"));
            cleared.put("recordings", count("recording"));
            cleared.put("classificationMemory", count("classification_memory"));
            db.update("DELETE FROM recording");
            db.update("DELETE FROM workflow_version");
            db.update("DELETE FROM workflow");
            db.update("DELETE FROM classification_memory");
            db.update("DELETE FROM user_preference");
            cleared.put("preferences", "cleared");
        }
        cleared.put("notice", "GhostShift deleted only its own database records and generated reports. "
                + "Your files were not deleted, moved, or modified.");
        activity.record("DATA", "Local data cleared", "Scope: " + scope
                + ". User files were not touched.", null);
        LOG.info("User cleared local GhostShift data with scope {}", scope);
        return cleared;
    }

    private long count(String table) {
        return db.queryOne("SELECT COUNT(*) FROM " + table, rs -> rs.getLong(1)).orElse(0L);
    }
}
