package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

@Serdeable
public record ErrorGroup(
        String signature,
        String sample,
        int count,
        String firstSeen,
        String lastSeen,
        List<Integer> lineNumbers
) {
}

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

@Serdeable
public record LogEvidence(
        LogAnalysis analysis,
        List<String> observations,
        List<String> possibleCauses,
        List<String> recommendedChecks,
        String aiNotice,
        boolean aiUsed
) {
}

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

@Serdeable
public record ReportExport(
        String fileName,
        String content,
        String contentType
) {
}

@Serdeable
public record AnalysisRequest(
        long directoryId,
        String relativePath,
        Boolean useAi
) {
}

@Serdeable
public record EvidenceBundle(Map<String, Object> values) {
}
