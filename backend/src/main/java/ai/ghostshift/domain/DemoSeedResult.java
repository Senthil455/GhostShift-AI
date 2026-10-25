package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DemoSeedResult(
        long directoryId,
        String directoryName,
        int filesCreated,
        int workflowsCreated,
        String message
) {
}
