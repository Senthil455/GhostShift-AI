package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AnalysisRequest(
        long directoryId,
        String relativePath,
        Boolean useAi
) {
}
