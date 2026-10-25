package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record HealthStatus(
        String status,
        String version,
        String database,
        String aiStatus,
        String aiModel,
        long uptimeSeconds,
        String timestamp
) {
}
