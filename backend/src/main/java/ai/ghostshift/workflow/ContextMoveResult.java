package ai.ghostshift.workflow;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ContextMoveResult(
        String sourceRelative,
        String destinationRelative,
        boolean moved,
        String error
) {
}
