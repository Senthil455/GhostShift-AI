package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record AiLogInsight(
        String summary,
        List<String> possibleCauses,
        List<String> recommendedChecks,
        double confidence,
        String notice
) {
}
