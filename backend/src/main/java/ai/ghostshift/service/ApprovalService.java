package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.domain.ActionExecution;
import ai.ghostshift.domain.Approval;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.Text;
import ai.ghostshift.workflow.WorkflowContext;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * Human approval resolution. Approving never executes the gated action in the
 * request thread: the workflow worker resumes and re-checks every boundary.
 */
@Singleton
public class ApprovalService {
    private final RunRepository runs;
    private final RunExecutor executor;
    private final JobRunner jobs;
    private final ActivityService activity;
    private final Json json;

    public ApprovalService(RunRepository runs, RunExecutor executor, JobRunner jobs,
                           ActivityService activity, Json json) {
        this.runs = runs;
        this.executor = executor;
        this.jobs = jobs;
        this.activity = activity;
        this.json = json;
    }

    public List<Approval> pending() {
        return runs.pendingApprovals();
    }

    public WorkflowRun decide(String approvalId, boolean approved, String note) {
        Approval approval = runs.approval(approvalId);
        if (!approval.status().equals("PENDING")) {
            throw ApiException.conflict("This approval has already been resolved.");
        }
        WorkflowRun run = runs.get(approval.runId());
        if (!run.status().equals("WAITING_FOR_APPROVAL")) {
            throw ApiException.conflict("This run is no longer waiting for approval.");
        }
        String resolution = approved ? "APPROVED" : "REJECTED";
        if (runs.resolveApproval(approvalId, resolution, note) == 0) {
            throw ApiException.conflict("This approval was resolved by another request.");
        }
        ActionExecution action = run.actions().stream()
                .filter(item -> item.id().equals(approval.actionExecutionId()))
                .findFirst()
                .orElse(null);
        String suffix = note == null || note.isBlank() ? "" : " Note: " + Text.truncate(note, 300);

        if (!approved) {
            if (action != null) {
                runs.updateAction(action.id(), "CANCELLED",
                        Map.of("rejected", true, "approvalId", approvalId),
                        "Rejected by the user." + suffix, null, Text.now());
            }
            runs.markRemainingCancelled(run.id());
            runs.finalizeRun(run.id(), "CANCELLED",
                    "Rejected by the user at approval '" + approval.title() + "'." + suffix,
                    runs.duration(run.id()));
            runs.addEvent(run.id(), "WARN", "Approval rejected: " + approval.title()
                    + ". No further actions will run." + suffix, Map.of("approvalId", approvalId));
            activity.record("APPROVAL", "Approval rejected",
                    run.workflowName() + " · " + approval.title(), run.id());
            return runs.get(run.id());
        }

        if (!runs.claimResume(run.id())) {
            throw ApiException.conflict("The run is no longer waiting for approval.");
        }
        if (action != null) {
            if ("FILE_MOVE".equals(approval.kind())) {
                runs.markActionStarted(action.id());
            } else {
                runs.updateAction(action.id(), "SUCCEEDED",
                        Map.of("approved", true, "approvalId", approvalId),
                        null, null, Text.now());
            }
        }
        WorkflowContext context = executor.loadContext(run.id()).withExplicitApproval(true);
        runs.saveContext(run.id(), json.write(context));
        runs.addEvent(run.id(), "INFO", "Approval granted: " + approval.title() + "." + suffix,
                Map.of("approvalId", approvalId, "kind", approval.kind()));
        activity.record("APPROVAL", "Approval granted",
                run.workflowName() + " · " + approval.title(), run.id());
        jobs.submit("resume workflow run " + run.id(), () -> executor.execute(run.id()));
        return runs.get(run.id());
    }
}
