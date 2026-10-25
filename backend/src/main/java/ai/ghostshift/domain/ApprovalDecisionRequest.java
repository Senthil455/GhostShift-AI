package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ApprovalDecisionRequest(
        String note
) {
}
