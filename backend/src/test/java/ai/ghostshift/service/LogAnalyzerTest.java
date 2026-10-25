package ai.ghostshift.service;

import ai.ghostshift.config.AppConfig;
import ai.ghostshift.domain.LogAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogAnalyzerTest {
    @Test
    void countsEachSeverityOnceAndIgnoresTheTrailingNewline() throws IOException {
        Path dir = Files.createTempDirectory("ghostshift-log-analyzer-");
        Path log = dir.resolve("sample.log");
        Files.writeString(log, """
                2026-03-10T09:15:01Z INFO  demo-app started
                2026-03-10T09:15:02Z ERROR demo-sync failed: connection refused
                2026-03-10T09:15:03Z WARN  demo-sync retry scheduled
                2026-03-10T09:15:05Z WARN  demo-app degraded mode
                2026-03-10T09:15:09Z ERROR request timed out after 2000 ms
                """);

        LogAnalysis analysis = analyzer(dir).analyze(log);

        assertEquals(5, analysis.totalLines());
        assertEquals(2, analysis.errorLines());
        assertEquals(2, analysis.warningLines(), "WARN lines must be counted exactly once");
        assertEquals(2, analysis.groups().size());
        assertNotNull(analysis.timelineNote());
        assertFalse(analysis.truncated());
        assertTrue(analysis.observations().stream()
                .anyMatch(observation -> observation.contains("2 warning line(s)")));
    }

    @Test
    void handlesAnEmptyFileWithoutCountingAPhantomLine() throws IOException {
        Path dir = Files.createTempDirectory("ghostshift-log-empty-");
        Path log = dir.resolve("empty.log");
        Files.writeString(log, "");

        LogAnalysis analysis = analyzer(dir).analyze(log);

        assertEquals(0, analysis.totalLines());
        assertEquals(0, analysis.errorLines());
        assertEquals(0, analysis.warningLines());
        assertTrue(analysis.groups().isEmpty());
    }

    private static LogAnalyzer analyzer(Path dataDir) {
        AppConfig config = new AppConfig(dataDir.toString(), 3, 1000, 2 * 1024 * 1024,
                "http://127.0.0.1:11434", "gemma2:2b", 120, 12000, "");
        return new LogAnalyzer(config);
    }
}
