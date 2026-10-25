package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record OrganizationPlanSummary(
        String id,
        long directoryId,
        String directoryName,
        String status,
        int fileCount,
        int conflictCount,
        String summary,
        String createdAt,
        String updatedAt,
        String executedAt
) {
}
