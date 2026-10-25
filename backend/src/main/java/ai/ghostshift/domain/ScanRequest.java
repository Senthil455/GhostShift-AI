package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ScanRequest(
        Boolean recursive,
        Boolean useAi
) {
}
