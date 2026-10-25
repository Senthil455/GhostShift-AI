package ai.ghostshift.config;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Immutable application configuration. Runtime model settings live in SQLite and
 * override the AI defaults below; this bean only owns filesystem locations and hard limits.
 */
@Singleton
public class AppConfig {
    public static final String VERSION = "1.0.0";

    private final Path dataDir;
    private final int maxScanDepth;
    private final int maxScanFiles;
    private final int maxLogBytes;
    private final String defaultAiEndpoint;
    private final String defaultAiModel;
    private final int aiTimeoutSeconds;
    private final int aiMaxInputChars;
    private final String apiToken;

    public AppConfig(
            @Value("${ghostshift.data-dir:data}") String dataDir,
            @Value("${ghostshift.max-scan-depth:3}") int maxScanDepth,
            @Value("${ghostshift.max-scan-files:1000}") int maxScanFiles,
            @Value("${ghostshift.max-log-bytes:2097152}") int maxLogBytes,
            @Value("${ghostshift.ai.endpoint:http://localhost:11434}") String defaultAiEndpoint,
            @Value("${ghostshift.ai.model:gemma2:2b}") String defaultAiModel,
            @Value("${ghostshift.ai.timeout-seconds:120}") int aiTimeoutSeconds,
            @Value("${ghostshift.ai.max-input-chars:12000}") int aiMaxInputChars,
            @Value("${ghostshift.api-token:}") @Nullable String apiToken) {
        this.dataDir = Path.of(dataDir).toAbsolutePath().normalize();
        this.maxScanDepth = Math.clamp(maxScanDepth, 1, 10);
        this.maxScanFiles = Math.clamp(maxScanFiles, 10, 10_000);
        this.maxLogBytes = Math.clamp(maxLogBytes, 64 * 1024, 32 * 1024 * 1024);
        this.defaultAiEndpoint = defaultAiEndpoint == null ? "http://localhost:11434" : defaultAiEndpoint.strip();
        this.defaultAiModel = defaultAiModel == null ? "gemma2:2b" : defaultAiModel.strip();
        this.aiTimeoutSeconds = Math.clamp(aiTimeoutSeconds, 5, 600);
        this.aiMaxInputChars = Math.clamp(aiMaxInputChars, 500, 200_000);
        this.apiToken = apiToken == null ? "" : apiToken.strip();
        try {
            Files.createDirectories(this.dataDir);
            Files.createDirectories(reportsDir());
            Files.createDirectories(demoDir());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create GhostShift data directory at " + this.dataDir + ": " + e.getMessage(), e);
        }
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path databasePath() {
        return dataDir.resolve("ghostshift.db");
    }

    public Path reportsDir() {
        return dataDir.resolve("reports");
    }

    public Path demoDir() {
        return dataDir.resolve("demo");
    }

    public int maxScanDepth() {
        return maxScanDepth;
    }

    public int maxScanFiles() {
        return maxScanFiles;
    }

    public int maxLogBytes() {
        return maxLogBytes;
    }

    public String defaultAiEndpoint() {
        return defaultAiEndpoint;
    }

    public String defaultAiModel() {
        return defaultAiModel;
    }

    public int aiTimeoutSeconds() {
        return aiTimeoutSeconds;
    }

    public int aiMaxInputChars() {
        return aiMaxInputChars;
    }

    public boolean apiTokenEnabled() {
        return !apiToken.isBlank();
    }

    public boolean tokenMatches(String supplied) {
        if (!apiTokenEnabled()) {
            return true;
        }
        return supplied != null && java.security.MessageDigest.isEqual(
                apiToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
