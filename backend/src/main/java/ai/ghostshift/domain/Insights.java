package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record RunSummary(
        String id,
        String workflowId,
        String workflowName,
        String status,
        boolean dryRun,
        String startedAt,
        String endedAt,
        Long durationMs,
        int totalSteps,
        int succeededSteps,
        int failedSteps,
        int waitingSteps,
        String summary
) {
}

@Serdeable
public record ActivityItem(
        long id,
        String kind,
        String title,
        String detail,
        String entityId,
        String at
) {
}

@Serdeable
public record Insight(
        String title,
        String detail,
        String severity
) {
}

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

@Serdeable
public record SavingsLine(
        String label,
        double manualMinutes,
        double actualMinutes,
        double savedMinutes,
        String basis
) {
}

@Serdeable
public record TimeSavingsView(
        double estimatedMinutesSaved,
        double manualMinutes,
        double actualMinutes,
        String method,
        String disclaimer,
        List<SavingsLine> lines
) {
}

@Serdeable
public record HistoryPage(
        List<ActivityItem> items,
        int page,
        int pageSize,
        long total
) {
}

@Serdeable
public record DuplicateFile(
        String relativePath,
        long sizeBytes,
        String modifiedAt
) {
}

@Serdeable
public record DuplicateGroup(
        String kind,
        String label,
        long sizeBytes,
        List<DuplicateFile> files
) {
}

@Serdeable
public record DuplicateReport(
        long directoryId,
        int filesHashed,
        int skippedLargeFiles,
        List<DuplicateGroup> groups,
        String notice
) {
}
