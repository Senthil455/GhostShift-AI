package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record RegisterDirectoryRequest(
        String path,
        String displayName,
        String directoryType,
        Boolean allowOrganize
) {
}

@Serdeable
public record ScanRequest(
        Boolean recursive,
        Boolean useAi
) {
}

@Serdeable
public record ScanResult(
        long directoryId,
        String directoryName,
        int fileCount,
        boolean truncated,
        int skippedSymlinks,
        java.util.List<FileInfo> files
) {
}

@Serdeable
public record OrganizationPlanRequest(
        long directoryId,
        Boolean useAi,
        Boolean recursive,
        Integer maxFiles,
        String recordingId
) {
}

@Serdeable
public record PlanDecision(
        String actionId,
        String decision,
        Category category,
        Boolean remember
) {
}

@Serdeable
public record PlanDecisionRequest(
        java.util.List<PlanDecision> decisions,
        Boolean rejectUnreviewed
) {
}

@Serdeable
public record PlanPatchRequest(
        Category category,
        String status,
        Boolean remember
) {
}

@Serdeable
public record WorkflowRequest(
        String name,
        String description,
        String category,
        WorkflowDefinition definition
) {
}

@Serdeable
public record WorkflowGenerateRequest(
        String prompt,
        Long directoryId
) {
}

@Serdeable
public record WorkflowRunRequest(
        Map<String, Object> inputs,
        Boolean dryRun,
        String recordingId
) {
}

@Serdeable
public record ApprovalDecisionRequest(
        String note
) {
}

@Serdeable
public record ApiError(
        String code,
        String message,
        Map<String, Object> details,
        String requestId,
        String timestamp
) {
}

@Serdeable
public record HealthStatus(
        String status,
        String version,
        String database,
        String aiStatus,
        String aiModel,
        long uptimeSeconds,
        String timestamp
) {
}

@Serdeable
public record DeleteDataRequest(
        String scope,
        String confirmation
) {
}

@Serdeable
public record DataExport(
        String generatedAt,
        String appVersion,
        Map<String, Object> data
) {
}

@Serdeable
public record DemoSeedResult(
        long directoryId,
        String directoryName,
        int filesCreated,
        int workflowsCreated,
        String message
) {
}
