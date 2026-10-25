package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ReportSummary(
        String id,
        String kind,
        String title,
        String sourceRelative,
        boolean aiUsed,
        String model,
        String createdAt
) {
}
