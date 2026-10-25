package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record WorkflowDetail(
        String id,
        String name,
        String description,
        String category,
        int currentVersion,
        String status,
        boolean archived,
        String createdAt,
        String updatedAt,
        WorkflowDefinition definition,
        WorkflowValidation validation,
        List<WorkflowVersionView> versions
) {
}
