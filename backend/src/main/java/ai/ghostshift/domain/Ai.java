package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record AiModelInfo(
        String name,
        long sizeBytes,
        String parameterSize,
        String quantization,
        String family
) {
}

@Serdeable
public record AiStatus(
        boolean reachable,
        boolean modelAvailable,
        String endpoint,
        String model,
        String status,
        String message,
        Long latencyMs,
        String checkedAt,
        List<AiModelInfo> models
) {
    public static AiStatus unavailable(String endpoint, String model, String message) {
        return new AiStatus(false, false, endpoint, model, "UNAVAILABLE", message, null,
                java.time.Instant.now().toString(), List.of());
    }
}

@Serdeable
public record AiTestResult(
        boolean ok,
        String endpoint,
        String model,
        long latencyMs,
        String message,
        String sample
) {
}

@Serdeable
public record AiClassification(
        String fileName,
        Category category,
        double confidence,
        String reason
) {
}

@Serdeable
public record AiLogInsight(
        String summary,
        List<String> possibleCauses,
        List<String> recommendedChecks,
        double confidence,
        String notice
) {
}

@Serdeable
public record AiWorkflowDraft(
        WorkflowDefinition definition,
        String explanation,
        WorkflowValidation validation,
        boolean aiUsed
) {
}

@Serdeable
public record ModelSettings(
        String provider,
        String endpoint,
        String model,
        int timeoutSeconds,
        int maxInputChars,
        String lastHealthStatus,
        String lastHealthAt,
        Long lastLatencyMs
) {
}

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

@Serdeable
public record ModelSettingsUpdate(
        String provider,
        String endpoint,
        String model,
        Integer timeoutSeconds,
        Integer maxInputChars
) {
}

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

@Serdeable
public record AiConnectionRequest(
        String endpoint,
        String model
) {
}
