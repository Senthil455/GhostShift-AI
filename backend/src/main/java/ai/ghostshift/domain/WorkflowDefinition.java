package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record WorkflowDefinition(
        int schemaVersion,
        String name,
        String description,
        List<WorkflowStep> steps,
        List<WorkflowInput> inputs,
        boolean requiresApproval,
        String failurePolicy
) {
}
