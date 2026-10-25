package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record TimeSavingsSettings(
        double fileReviewMinutesPerFile,
        double logAnalysisMinutes,
        double workflowReviewMinutes
) {
    public static TimeSavingsSettings defaults() {
        return new TimeSavingsSettings(1.5, 20.0, 5.0);
    }
}
