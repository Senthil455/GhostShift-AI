package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record WorkflowInput(
        String name,
        String label,
        String type,
        boolean required,
        String description
) {
}
