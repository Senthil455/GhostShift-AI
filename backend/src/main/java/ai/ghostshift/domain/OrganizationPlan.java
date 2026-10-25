package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record OrganizationPlan(
        String id,
        long directoryId,
        String directoryName,
        String directoryPath,
        String status,
        int fileCount,
        int conflictCount,
        String summary,
        String createdAt,
        String updatedAt,
        String executedAt,
        List<PlanAction> actions
) {
}
