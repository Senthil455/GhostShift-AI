package ai.ghostshift.service;

import ai.ghostshift.ai.GhostBrainService;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.ActivityItem;
import ai.ghostshift.domain.AiStatus;
import ai.ghostshift.domain.DashboardSummary;
import ai.ghostshift.domain.DuplicateReport;
import ai.ghostshift.domain.HistoryPage;
import ai.ghostshift.domain.Insight;
import ai.ghostshift.domain.RunSummary;
import ai.ghostshift.domain.SavingsLine;
import ai.ghostshift.domain.TimeSavingsSettings;
import ai.ghostshift.domain.TimeSavingsView;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Deterministic local metrics. Time savings are explicitly labelled estimates. */
@Singleton
public class InsightService {
    private final Database db;
    private final GhostBrainService brain;
    private final SettingsService settings;
    private final ActivityService activity;
    private final DuplicateService duplicates;
    private final RunRepository runs;

    public InsightService(Database db, GhostBrainService brain, SettingsService settings,
                          ActivityService activity, DuplicateService duplicates, RunRepository runs) {
        this.db = db;
        this.brain = brain;
        this.settings = settings;
        this.activity = activity;
        this.duplicates = duplicates;
        this.runs = runs;
    }

    public DashboardSummary dashboard() {
        long totalWorkflows = count("SELECT COUNT(*) FROM workflow WHERE archived = 0");
        long totalRuns = count("SELECT COUNT(*) FROM workflow_run");
        long completed = count("SELECT COUNT(*) FROM workflow_run WHERE status = 'COMPLETED'");
        long needsIntervention = count("""
                SELECT COUNT(*) FROM workflow_run
                WHERE status IN ('WAITING_FOR_APPROVAL', 'PARTIALLY_COMPLETED', 'FAILED')
                """);
        long filesScanned = count("SELECT COUNT(*) FROM organization_action");
        long filesOrganized = count("SELECT COUNT(*) FROM file_operation WHERE status = 'COMPLETED'");
        long reports = count("SELECT COUNT(*) FROM report");
        long pendingApprovals = count("SELECT COUNT(*) FROM approval_request WHERE status = 'PENDING'");
        long recentFailures = count("SELECT COUNT(*) FROM workflow_run WHERE status = 'FAILED'");
        TimeSavingsView savings = timeSavings();
        AiStatus ai = brain.status(false);
        List<RunSummary> recentRuns = runs.listSummaries(6);
        List<ActivityItem> recentActivity = activity.recent(8);
        List<Insight> insights = new ArrayList<>();
        if (pendingApprovals > 0) {
            insights.add(new Insight("Approvals are waiting",
                    pendingApprovals + " action(s) need a human decision before GhostShift can continue.",
                    "ATTENTION"));
        }
        if (recentFailures > 0) {
            insights.add(new Insight("Review failed runs",
                    recentFailures + " workflow run(s) failed. Open History for the exact step and error.",
                    "ATTENTION"));
        }
        if (!ai.reachable()) {
            insights.add(new Insight("Local model is offline",
                    "GhostShift remains fully usable with deterministic rules. Start Ollama to enable GhostBrain.",
                    "INFO"));
        } else if (!ai.modelAvailable()) {
            insights.add(new Insight("Selected model is not installed",
                    "Ollama answered, but the configured model is missing. Install it or choose another model.",
                    "WARNING"));
        }
        long directories = count("SELECT COUNT(*) FROM registered_directory WHERE active = 1");
        if (directories == 0) {
            insights.add(new Insight("Register your first folder",
                    "GhostShift can only read inside folders you explicitly register.", "INFO"));
        }
        if (totalWorkflows == 0) {
            insights.add(new Insight("No workflows yet",
                    "Start from an example workflow or ask GhostBrain to draft one for review.", "INFO"));
        }
        return new DashboardSummary(totalWorkflows, totalRuns, completed, needsIntervention,
                filesScanned, filesOrganized, reports, pendingApprovals, recentFailures,
                round(savings.estimatedMinutesSaved()), ai.status(), ai.model(),
                recentRuns, recentActivity, insights);
    }

    public TimeSavingsView timeSavings() {
        TimeSavingsSettings assumptions = settings.timeSavings();
        long movedFiles = count("SELECT COUNT(*) FROM file_operation WHERE status = 'COMPLETED'");
        long plans = count("""
                SELECT COUNT(*) FROM organization_plan
                WHERE executed_at IS NOT NULL AND status IN ('COMPLETED', 'PARTIALLY_COMPLETED')
                """);
        double planMinutes = db.queryOne("""
                        SELECT COALESCE(SUM((julianday(updated_at) - julianday(executed_at)) * 1440.0), 0)
                        FROM organization_plan
                        WHERE executed_at IS NOT NULL AND status IN ('COMPLETED', 'PARTIALLY_COMPLETED')
                        """, rs -> rs.getDouble(1)).orElse(0.0);
        long logReports = count("SELECT COUNT(*) FROM report WHERE kind = 'LOG_ANALYSIS'");
        long completedRuns = count("""
                SELECT COUNT(*) FROM workflow_run
                WHERE status IN ('COMPLETED', 'PARTIALLY_COMPLETED') AND dry_run = 0
                """);
        double runMinutes = db.queryOne("""
                        SELECT COALESCE(SUM(duration_ms), 0) / 60000.0 FROM workflow_run
                        WHERE duration_ms IS NOT NULL AND dry_run = 0
                          AND status IN ('COMPLETED', 'PARTIALLY_COMPLETED')
                        """, rs -> rs.getDouble(1)).orElse(0.0);

        List<SavingsLine> lines = new ArrayList<>();
        double fileManual = movedFiles * assumptions.fileReviewMinutesPerFile();
        lines.add(new SavingsLine("File organization", round(fileManual), round(planMinutes),
                round(fileManual - planMinutes),
                movedFiles + " moved file(s) × " + assumptions.fileReviewMinutesPerFile()
                        + " min manual review assumption; only organization-plan wall time is measured."));
        double logManual = logReports * assumptions.logAnalysisMinutes();
        lines.add(new SavingsLine("Log analysis reports", round(logManual), 0.0, round(logManual),
                logReports + " report(s) × " + assumptions.logAnalysisMinutes()
                        + " min assumption. Report authoring time is not timed separately."));
        double workflowManual = completedRuns * assumptions.workflowReviewMinutes();
        lines.add(new SavingsLine("Workflow review overhead", round(workflowManual), round(runMinutes),
                round(workflowManual - runMinutes),
                completedRuns + " completed run(s) × " + assumptions.workflowReviewMinutes()
                        + " min assumption versus measured run duration."));

        double manual = round(fileManual + logManual + workflowManual);
        double actual = round(planMinutes + runMinutes);
        double saved = round(manual - actual);
        return new TimeSavingsView(saved, manual, actual, "ESTIMATE_V1",
                "These are estimates based on your configured assumptions and GhostShift's local history. "
                        + "They are not a verified time study. Actual time is measured only where a run or plan "
                        + "records a duration; report authoring is not timed separately.",
                lines);
    }

    public DuplicateReport duplicates(long directoryId, boolean recursive) {
        return duplicates.inspect(directoryId, recursive);
    }

    public HistoryPage history(int page, int pageSize, String kind) {
        return activity.page(page, pageSize, kind == null || kind.isBlank()
                ? null : kind.strip().toUpperCase(Locale.ROOT));
    }

    private long count(String sql) {
        return db.queryOne(sql, rs -> rs.getLong(1)).orElse(0L);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
