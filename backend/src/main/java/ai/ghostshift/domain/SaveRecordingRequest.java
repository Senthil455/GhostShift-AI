package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SaveRecordingRequest(
        String name,
        String description,
        String category
) {
}
