package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record WorkflowGenerateRequest(
        String prompt,
        Long directoryId
) {
}
