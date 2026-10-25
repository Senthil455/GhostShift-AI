package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RegisteredDirectory(
        long id,
        String displayName,
        String path,
        String directoryType,
        boolean allowRead,
        boolean allowOrganize,
        boolean active,
        String createdAt,
        String lastScanAt
) {
}
