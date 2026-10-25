package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record OrganizationPlanRequest(
        long directoryId,
        Boolean useAi,
        Boolean recursive,
        Integer maxFiles,
        String recordingId
) {
}
