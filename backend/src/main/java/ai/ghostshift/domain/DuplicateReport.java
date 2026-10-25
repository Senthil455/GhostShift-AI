package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record DuplicateReport(
        long directoryId,
        int filesHashed,
        int skippedLargeFiles,
        List<DuplicateGroup> groups,
        String notice
) {
}
