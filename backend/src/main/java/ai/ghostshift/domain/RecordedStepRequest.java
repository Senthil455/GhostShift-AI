package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record RecordedStepRequest(
        String actionType,
        String description,
        Map<String, Object> parameters
) {
}
