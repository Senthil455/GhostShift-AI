package ai.ghostshift.domain;

import ai.ghostshift.domain.LogAnalysis;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record LogEvidence(
        LogAnalysis analysis,
        List<String> observations,
        List<String> possibleCauses,
        List<String> recommendedChecks,
        String aiNotice,
        boolean aiUsed
) {
}
