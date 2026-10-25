package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ModelSettingsUpdate(
        String provider,
        String endpoint,
        String model,
        Integer timeoutSeconds,
        Integer maxInputChars
) {
}
