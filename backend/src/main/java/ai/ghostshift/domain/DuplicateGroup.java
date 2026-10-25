package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record DuplicateGroup(
        String kind,
        String label,
        long sizeBytes,
        List<DuplicateFile> files
) {
}
