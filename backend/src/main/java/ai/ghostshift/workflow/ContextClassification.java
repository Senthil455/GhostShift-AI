package ai.ghostshift.workflow;

import ai.ghostshift.domain.Category;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ContextClassification(
        Category category,
        double confidence,
        String rationale,
        String source
) {
}
