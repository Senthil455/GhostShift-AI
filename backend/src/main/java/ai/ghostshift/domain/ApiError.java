package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        String requestId,
        String timestamp
) {
}
