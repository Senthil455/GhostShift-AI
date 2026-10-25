package ai.ghostshift.workflow;

import ai.ghostshift.domain.Category;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ContextMove(
        String sourceRelative,
        String destinationRelative,
        String fileName,
        long sizeBytes,
        String modifiedAt,
        Category category,
        boolean conflict
) {
}
