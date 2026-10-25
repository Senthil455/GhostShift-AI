package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record ActionExecution(
        String id,
        String stepId,
        int stepIndex,
        String actionType,
        String status,
        Map<String, Object> parameters,
        Map<String, Object> result,
        String error,
        String startedAt,
        String endedAt
) {
}
