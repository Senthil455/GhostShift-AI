package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record StartRecordingRequest(
        String name,
        String mode,
        Long directoryId
) {
}
