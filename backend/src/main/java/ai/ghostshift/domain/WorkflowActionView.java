package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record WorkflowActionView(
        String type,
        String label,
        String risk,
        String permission,
        boolean requiresApproval,
        String description,
        Map<String, Map<String, Object>> parameters
) {
}
