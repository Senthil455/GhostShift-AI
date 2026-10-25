package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record PlanDecisionRequest(
        java.util.List<PlanDecision> decisions,
        Boolean rejectUnreviewed
) {
}
