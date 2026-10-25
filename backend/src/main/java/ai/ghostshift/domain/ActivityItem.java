package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ActivityItem(
        long id,
        String kind,
        String title,
        String detail,
        String entityId,
        String at
) {
}
