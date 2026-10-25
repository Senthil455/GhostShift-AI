package ai.ghostshift.domain;

import ai.ghostshift.domain.Category;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AiClassification(
        String fileName,
        Category category,
        double confidence,
        String reason
) {
}
