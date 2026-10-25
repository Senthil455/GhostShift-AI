package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RegisterDirectoryRequest(
        String path,
        String displayName,
        String directoryType,
        Boolean allowOrganize
) {
}
