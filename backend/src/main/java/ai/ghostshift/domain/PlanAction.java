package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record PlanAction(
        String id,
        String sourceRelative,
        String destinationRelative,
        String fileName,
        String extension,
        long sizeBytes,
        String modifiedAt,
        Category category,
        double confidence,
        String rationale,
        String classificationSource,
        String status,
        boolean conflict,
        String error
) {
}
