package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Insight(
        String title,
        String detail,
        String severity
) {
}
