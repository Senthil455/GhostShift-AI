package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record AppSettings(
        ModelSettings model,
        ApprovalPolicy approvals,
        TimeSavingsSettings timeSavings,
        String theme,
        boolean onboardingCompleted,
        String dataDir,
        String databasePath,
        java.util.Map<String, String> categoryFolders
) {
}
