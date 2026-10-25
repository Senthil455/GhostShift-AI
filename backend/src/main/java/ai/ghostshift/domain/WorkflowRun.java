package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
public record WorkflowRun(
        String id,
        String workflowId,
        String workflowName,
        int workflowVersion,
        String status,
        boolean dryRun,
        String triggerType,
        Map<String, Object> inputs,
        String summary,
        boolean cancelRequested,
        int currentStep,
        String startedAt,
        String endedAt,
        Long durationMs,
        List<ActionExecution> actions,
        List<Approval> approvals
) {
}

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

@Serdeable
public record RunEvent(
        long id,
        String runId,
        String at,
        String level,
        String message,
        Map<String, Object> data
) {
}

@Serdeable
public record Approval(
        String id,
        String runId,
        String actionExecutionId,
        String workflowName,
        String kind,
        String title,
        String explanation,
        String risk,
        String status,
        String resolutionNote,
        String createdAt,
        String resolvedAt
) {
}
