package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DuplicateFile(
        String relativePath,
        long sizeBytes,
        String modifiedAt
) {
}
