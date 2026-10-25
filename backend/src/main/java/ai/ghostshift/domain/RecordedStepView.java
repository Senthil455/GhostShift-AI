package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record RecordedStepView(
        String id,
        int order,
        String actionType,
        String description,
        Map<String, Object> parameters,
        boolean requiresApproval
) {
}
