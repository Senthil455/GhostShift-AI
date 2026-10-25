package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.config.AppConfig;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.AiStatus;
import ai.ghostshift.domain.AppSettings;
import ai.ghostshift.domain.ApprovalPolicy;
import ai.ghostshift.domain.Category;
import ai.ghostshift.domain.ModelSettings;
import ai.ghostshift.domain.ModelSettingsUpdate;
import ai.ghostshift.domain.SettingsUpdateRequest;
import ai.ghostshift.domain.TimeSavingsSettings;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Singleton
public class SettingsService {
    private static final String KEY_APPROVALS = "approval.policy";
    private static final String KEY_TIME_SAVINGS = "time.savings";
    private static final String KEY_THEME = "ui.theme";
    private static final String KEY_ONBOARDING = "onboarding.completed";
    private static final String KEY_CATEGORY_FOLDERS = "category.folders";

    private final Database db;
    private final Json json;
    private final AppConfig config;

    public SettingsService(Database db, Json json, AppConfig config) {
        this.db = db;
        this.json = json;
        this.config = config;
        initialize();
    }

    private void initialize() {
        db.update("""
                INSERT OR IGNORE INTO model_configuration
                    (id, provider, endpoint, model, timeout_seconds, max_input_chars)
                VALUES (1, 'ollama', ?, ?, ?, ?)
                """, config.defaultAiEndpoint(), config.defaultAiModel(),
                config.aiTimeoutSeconds(), config.aiMaxInputChars());
    }

    public ModelSettings modelSettings() {
        return db.queryOne("""
                        SELECT provider, endpoint, model, timeout_seconds, max_input_chars,
                               last_health_status, last_health_at, last_latency_ms
                        FROM model_configuration WHERE id = 1
                        """,
                rs -> new ModelSettings(
                        rs.getString("provider"),
                        rs.getString("endpoint"),
                        rs.getString("model"),
                        rs.getInt("timeout_seconds"),
                        rs.getInt("max_input_chars"),
                        rs.getString("last_health_status"),
                        rs.getString("last_health_at"),
                        (Long) rs.getObject("last_latency_ms")
                )).orElseThrow(() -> ApiException.internal("Model configuration is missing."));
    }

    public void recordModelHealth(AiStatus status) {
        db.update("""
                        UPDATE model_configuration
                        SET last_health_status = ?, last_health_at = ?, last_latency_ms = ?
                        WHERE id = 1
                        """, status.status(), status.checkedAt(), status.latencyMs());
    }

    public ApprovalPolicy approvals() {
        return read(KEY_APPROVALS, ApprovalPolicy.class, ApprovalPolicy.defaults());
    }

    public TimeSavingsSettings timeSavings() {
        return read(KEY_TIME_SAVINGS, TimeSavingsSettings.class, TimeSavingsSettings.defaults());
    }

    public String theme() {
        String value = preference(KEY_THEME);
        return value == null ? "dark" : json.read(value, String.class);
    }

    public boolean onboardingCompleted() {
        String value = preference(KEY_ONBOARDING);
        return value != null && json.read(value, Boolean.class);
    }

    public Map<String, String> categoryFolders() {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            defaults.put(category.name(), category.folder());
        }
        String value = preference(KEY_CATEGORY_FOLDERS);
        if (value == null) {
            return defaults;
        }
        try {
            Map<String, String> custom = json.readObject(value).entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof String)
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> (String) entry.getValue(),
                            (a, b) -> b,
                            LinkedHashMap::new));
            defaults.putAll(custom);
        } catch (RuntimeException ignored) {
            // A corrupted preference must not prevent the app from starting.
        }
        return defaults;
    }

    public String folderFor(Category category) {
        String configured = categoryFolders().get(category.name());
        return sanitizeFolderName(configured, category.folder());
    }

    public AppSettings settings() {
        return new AppSettings(
                modelSettings(),
                approvals(),
                timeSavings(),
                theme(),
                onboardingCompleted(),
                config.dataDir().toString(),
                config.databasePath().toString(),
                categoryFolders()
        );
    }

    public AppSettings update(SettingsUpdateRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Settings payload is required.");
        }
        if (request.model() != null) {
            updateModel(request.model());
        }
        if (request.approvals() != null) {
            write(KEY_APPROVALS, request.approvals());
        }
        if (request.timeSavings() != null) {
            validateTimeSavings(request.timeSavings());
            write(KEY_TIME_SAVINGS, request.timeSavings());
        }
        if (request.theme() != null) {
            String theme = request.theme().toLowerCase(Locale.ROOT);
            if (!theme.equals("dark") && !theme.equals("light")) {
                throw ApiException.badRequest("Theme must be dark or light.");
            }
            write(KEY_THEME, theme);
        }
        if (request.onboardingCompleted() != null) {
            write(KEY_ONBOARDING, request.onboardingCompleted());
        }
        if (request.categoryFolders() != null) {
            Map<String, String> sanitized = sanitizeCategoryFolders(request.categoryFolders());
            write(KEY_CATEGORY_FOLDERS, sanitized);
        }
        return settings();
    }

    private void updateModel(ModelSettingsUpdate update) {
        ModelSettings current = modelSettings();
        String endpoint = update.endpoint() == null ? current.endpoint() : update.endpoint().strip();
        String model = update.model() == null ? current.model() : update.model().strip();
        String provider = update.provider() == null ? current.provider() : update.provider().strip().toLowerCase(Locale.ROOT);
        int timeout = update.timeoutSeconds() == null ? current.timeoutSeconds() : update.timeoutSeconds();
        int maxInput = update.maxInputChars() == null ? current.maxInputChars() : update.maxInputChars();
        validateEndpoint(endpoint);
        if (model.isBlank() || model.length() > 120) {
            throw ApiException.badRequest("Choose a model name between 1 and 120 characters.");
        }
        if (!provider.equals("ollama")) {
            throw ApiException.badRequest("This version supports Ollama as the local model runtime.");
        }
        if (timeout < 5 || timeout > 600) {
            throw ApiException.badRequest("Inference timeout must be between 5 and 600 seconds.");
        }
        if (maxInput < 500 || maxInput > 200_000) {
            throw ApiException.badRequest("Maximum input size must be between 500 and 200,000 characters.");
        }
        db.update("""
                        UPDATE model_configuration
                        SET provider = ?, endpoint = ?, model = ?, timeout_seconds = ?, max_input_chars = ?,
                            last_health_status = NULL, last_health_at = NULL, last_latency_ms = NULL
                        WHERE id = 1
                        """, provider, endpoint, model, timeout, maxInput);
    }

    public static void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw ApiException.badRequest("The model endpoint is required.");
        }
        try {
            URI uri = URI.create(endpoint.strip());
            if (uri.getScheme() == null || (!uri.getScheme().equals("http") && !uri.getScheme().equals("https"))
                    || uri.getHost() == null) {
                throw ApiException.badRequest("The model endpoint must be a complete http:// or https:// URL.");
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("The model endpoint is not a valid URL.");
        }
    }

    private void validateTimeSavings(TimeSavingsSettings settings) {
        if (settings.fileReviewMinutesPerFile() < 0 || settings.fileReviewMinutesPerFile() > 600
                || settings.logAnalysisMinutes() < 0 || settings.logAnalysisMinutes() > 1440
                || settings.workflowReviewMinutes() < 0 || settings.workflowReviewMinutes() > 1440) {
            throw ApiException.badRequest("Time estimates must be between 0 and 1,440 minutes.");
        }
    }

    private Map<String, String> sanitizeCategoryFolders(Map<String, String> supplied) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : supplied.entrySet()) {
            Category category;
            try {
                category = Category.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("Unknown category: " + Text.truncate(entry.getKey(), 40));
            }
            result.put(category.name(), sanitizeFolderName(entry.getValue(), category.folder()));
        }
        return result;
    }

    private static String sanitizeFolderName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String safe = value.strip().replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "-")
                .replaceAll("\\.{2,}", ".");
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            return fallback;
        }
        return Text.truncate(safe, 80);
    }

    private String preference(String key) {
        return db.queryOne("SELECT value_json FROM user_preference WHERE preference_key = ?",
                rs -> rs.getString(1), key).orElse(null);
    }

    private <T> T read(String key, Class<T> type, T fallback) {
        String raw = preference(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return json.read(raw, type);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private void write(String key, Object value) {
        db.update("""
                        INSERT INTO user_preference(preference_key, value_json, updated_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT(preference_key) DO UPDATE SET
                            value_json = excluded.value_json,
                            updated_at = excluded.updated_at
                        """, key, json.write(value), Text.now());
    }
}
