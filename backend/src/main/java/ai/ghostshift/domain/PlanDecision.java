package ai.ghostshift.domain;

import ai.ghostshift.domain.Category;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PlanDecision(
        String actionId,
        String decision,
        Category category,
        Boolean remember
) {
}
