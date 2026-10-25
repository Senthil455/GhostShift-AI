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
