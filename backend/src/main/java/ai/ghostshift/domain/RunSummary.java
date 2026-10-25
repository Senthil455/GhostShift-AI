package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

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
