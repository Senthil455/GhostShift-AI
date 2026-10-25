package ai.ghostshift.api;

import ai.ghostshift.domain.Approval;
import ai.ghostshift.domain.ApprovalDecisionRequest;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.service.ApprovalService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;

import java.util.List;

@Controller("/api/v1/approvals")
public class ApprovalController {
    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @Get
    public List<Approval> pending() {
        return approvals.pending();
    }

    @Post("/{id}/approve")
    public WorkflowRun approve(@PathVariable String id,
                               @Nullable @Body ApprovalDecisionRequest request) {
        return approvals.decide(id, true, request == null ? null : request.note());
    }

    @Post("/{id}/reject")
    public WorkflowRun reject(@PathVariable String id,
                              @Nullable @Body ApprovalDecisionRequest request) {
        return approvals.decide(id, false, request == null ? null : request.note());
    }
}
