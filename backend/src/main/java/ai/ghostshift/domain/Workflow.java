package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
public record WorkflowInput(
        String name,
        String label,
        String type,
        boolean required,
        String description
) {
}

@Serdeable
public record WorkflowStep(
        String id,
        String type,
        String description,
        Map<String, Object> parameters,
        boolean enabled
) {
}

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

@Serdeable
public record WorkflowValidation(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        List<String> requiredPermissions,
        List<String> riskSummary
) {
}

@Serdeable
public record WorkflowSummary(
        String id,
        String name,
        String description,
        String category,
        int currentVersion,
        String status,
        boolean archived,
        String createdAt,
        String updatedAt,
        int stepCount,
        List<String> requiredPermissions,
        WorkflowValidation validation
) {
}

@Serdeable
public record WorkflowDetail(
        String id,
        String name,
        String description,
        String category,
        int currentVersion,
        String status,
        boolean archived,
        String createdAt,
        String updatedAt,
        WorkflowDefinition definition,
        WorkflowValidation validation,
        List<WorkflowVersionView> versions
) {
}

@Serdeable
public record WorkflowVersionView(
        String id,
        int version,
        int schemaVersion,
        String createdAt,
        WorkflowValidation validation
) {
}

@Serdeable
public record WorkflowStatusRequest(
        String status
) {
}

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
