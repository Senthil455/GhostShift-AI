package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record DashboardSummary(
        long totalWorkflows,
        long totalRuns,
        long completedRuns,
        long runsNeedingIntervention,
        long filesScanned,
        long filesOrganized,
        long reportsGenerated,
        long pendingApprovals,
        long recentFailures,
        double estimatedMinutesSaved,
        String aiStatus,
        String aiModel,
        List<RunSummary> recentRuns,
        List<ActivityItem> recentActivity,
        List<Insight> insights
) {
}
