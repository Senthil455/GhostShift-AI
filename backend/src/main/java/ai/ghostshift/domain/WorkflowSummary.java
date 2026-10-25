package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record WorkflowSummary(
        String id,
        String name,
        String description,
        String category,
        int currentVersion,
        String status,
        boolean archived,
        String createdAt,
        String updatedAt,
        int stepCount,
        List<String> requiredPermissions,
        WorkflowValidation validation
) {
}
