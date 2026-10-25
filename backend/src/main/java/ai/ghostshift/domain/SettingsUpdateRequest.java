package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record SettingsUpdateRequest(
        ModelSettingsUpdate model,
        ApprovalPolicy approvals,
        TimeSavingsSettings timeSavings,
        String theme,
        Boolean onboardingCompleted,
        java.util.Map<String, String> categoryFolders
) {
}
