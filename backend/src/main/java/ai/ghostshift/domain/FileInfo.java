package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record FileInfo(
        String id,
        String name,
        String relativePath,
        String extension,
        long sizeBytes,
        String modifiedAt
) {
}
