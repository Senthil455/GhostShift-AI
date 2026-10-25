package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record WorkflowRequest(
        String name,
        String description,
        String category,
        WorkflowDefinition definition
) {
}
