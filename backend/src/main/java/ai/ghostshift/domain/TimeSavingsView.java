package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record TimeSavingsView(
        double estimatedMinutesSaved,
        double manualMinutes,
        double actualMinutes,
        String method,
        String disclaimer,
        List<SavingsLine> lines
) {
}
