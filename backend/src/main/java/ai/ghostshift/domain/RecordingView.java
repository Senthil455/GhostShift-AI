package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

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
