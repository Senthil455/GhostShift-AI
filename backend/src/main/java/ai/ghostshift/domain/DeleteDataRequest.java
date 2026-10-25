package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DeleteDataRequest(
        String scope,
        String confirmation
) {
}
