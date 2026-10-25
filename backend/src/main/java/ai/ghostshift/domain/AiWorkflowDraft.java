package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AiWorkflowDraft(
        WorkflowDefinition definition,
        String explanation,
        WorkflowValidation validation,
        boolean aiUsed
) {
}
