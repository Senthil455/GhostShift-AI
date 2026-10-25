package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
public record RecordingView(
        String id,
        String name,
        String mode,
        String status,
        Long directoryId,
        String createdAt,
        String updatedAt,
        String stoppedAt,
        String workflowId,
        List<RecordedStepView> steps
) {
}

@Serdeable
public record RecordedStepView(
        String id,
        int order,
        String actionType,
        String description,
        Map<String, Object> parameters,
        boolean requiresApproval
) {
}

@Serdeable
public record RecordedStepRequest(
        String actionType,
        String description,
        Map<String, Object> parameters
) {
}

@Serdeable
public record UpdateRecordedStepRequest(
        String description,
        Integer order,
        Map<String, Object> parameters
) {
}

@Serdeable
public record StartRecordingRequest(
        String name,
        String mode,
        Long directoryId
) {
}

@Serdeable
public record SaveRecordingRequest(
        String name,
        String description,
        String category
) {
}
