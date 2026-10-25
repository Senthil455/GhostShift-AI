package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record WorkflowRunRequest(
        Map<String, Object> inputs,
        Boolean dryRun,
        String recordingId
) {
}
