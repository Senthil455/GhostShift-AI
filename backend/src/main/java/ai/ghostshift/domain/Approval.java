package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Approval(
        String id,
        String runId,
        String actionExecutionId,
        String workflowName,
        String kind,
        String title,
        String explanation,
        String risk,
        String status,
        String resolutionNote,
        String createdAt,
        String resolvedAt
) {
}
