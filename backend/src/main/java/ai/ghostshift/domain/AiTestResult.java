package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AiTestResult(
        boolean ok,
        String endpoint,
        String model,
        long latencyMs,
        String message,
        String sample
) {
}
