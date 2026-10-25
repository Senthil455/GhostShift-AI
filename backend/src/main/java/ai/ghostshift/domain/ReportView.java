package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ReportView(
        String id,
        String kind,
        String title,
        String sourceRelative,
        String contentMarkdown,
        LogEvidence evidence,
        boolean aiUsed,
        String model,
        String createdAt
) {
}
