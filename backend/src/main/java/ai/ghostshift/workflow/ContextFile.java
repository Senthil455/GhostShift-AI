package ai.ghostshift.workflow;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ContextFile(
        String id,
        String relativePath,
        String name,
        String extension,
        long sizeBytes,
        String modifiedAt
) {
}
