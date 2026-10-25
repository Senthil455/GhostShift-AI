package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record WorkflowStep(
        String id,
        String type,
        String description,
        Map<String, Object> parameters,
        boolean enabled
) {
}
