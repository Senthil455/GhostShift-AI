package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record WorkflowVersionView(
        String id,
        int version,
        int schemaVersion,
        String createdAt,
        WorkflowValidation validation
) {
}
