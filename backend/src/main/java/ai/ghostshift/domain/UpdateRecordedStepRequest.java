package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record UpdateRecordedStepRequest(
        String description,
        Integer order,
        Map<String, Object> parameters
) {
}
