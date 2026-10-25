package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ModelSettings(
        String provider,
        String endpoint,
        String model,
        int timeoutSeconds,
        int maxInputChars,
        String lastHealthStatus,
        String lastHealthAt,
        Long lastLatencyMs
) {
}
