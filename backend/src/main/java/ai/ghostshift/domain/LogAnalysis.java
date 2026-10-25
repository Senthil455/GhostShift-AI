package ai.ghostshift.domain;

import ai.ghostshift.domain.LogAnalysis;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record LogAnalysis(
        String fileName,
        int totalLines,
        int errorLines,
        int warningLines,
        long bytesRead,
        boolean truncated,
        List<ErrorGroup> groups,
        String timelineNote,
        List<String> observations
) {
}
