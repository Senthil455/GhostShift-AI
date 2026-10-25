package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ReportExport(
        String fileName,
        String content,
        String contentType
) {
}
