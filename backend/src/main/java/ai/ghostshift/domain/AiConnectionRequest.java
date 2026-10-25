package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AiConnectionRequest(
        String endpoint,
        String model
) {
}
