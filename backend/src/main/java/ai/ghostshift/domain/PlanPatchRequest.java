package ai.ghostshift.domain;

import ai.ghostshift.domain.Category;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PlanPatchRequest(
        Category category,
        String status,
        Boolean remember
) {
}
