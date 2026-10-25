package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record DataExport(
        String generatedAt,
        String appVersion,
        Map<String, Object> data
) {
}
