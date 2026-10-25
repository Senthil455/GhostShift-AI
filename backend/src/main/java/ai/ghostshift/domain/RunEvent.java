package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record RunEvent(
        long id,
        String runId,
        String at,
        String level,
        String message,
        Map<String, Object> data
) {
}
