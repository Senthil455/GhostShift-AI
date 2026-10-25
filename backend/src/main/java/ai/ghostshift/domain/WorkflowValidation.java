package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record WorkflowValidation(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        List<String> requiredPermissions,
        List<String> riskSummary
) {
}
