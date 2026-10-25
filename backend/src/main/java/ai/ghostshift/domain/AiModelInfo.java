package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AiModelInfo(
        String name,
        long sizeBytes,
        String parameterSize,
        String quantization,
        String family
) {
}
