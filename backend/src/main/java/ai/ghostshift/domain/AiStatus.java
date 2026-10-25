package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record AiStatus(
        boolean reachable,
        boolean modelAvailable,
        String endpoint,
        String model,
        String status,
        String message,
        Long latencyMs,
        String checkedAt,
        List<AiModelInfo> models
) {
    public static AiStatus unavailable(String endpoint, String model, String message) {
        return new AiStatus(false, false, endpoint, model, "UNAVAILABLE", message, null,
                java.time.Instant.now().toString(), List.of());
    }
}
