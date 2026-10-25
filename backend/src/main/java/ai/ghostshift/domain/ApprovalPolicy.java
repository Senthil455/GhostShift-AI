package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ApprovalPolicy(
        boolean fileMovesRequireApproval,
        boolean workflowMovesRequireApproval,
        boolean logAnalysisRequiresApproval
) {
    public static ApprovalPolicy defaults() {
        return new ApprovalPolicy(true, true, false);
    }
}
