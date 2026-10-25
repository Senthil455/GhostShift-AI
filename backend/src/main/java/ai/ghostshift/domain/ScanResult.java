package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

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
