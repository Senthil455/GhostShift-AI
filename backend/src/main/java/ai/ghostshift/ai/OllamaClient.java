package ai.ghostshift.ai;

import ai.ghostshift.config.AppConfig;
import ai.ghostshift.domain.AiModelInfo;
import ai.ghostshift.domain.AiStatus;
import ai.ghostshift.domain.AiTestResult;
import ai.ghostshift.domain.ModelSettings;
import ai.ghostshift.util.Json;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter for an Ollama-compatible local inference runtime. No model SDK, no secrets,
 * no hidden network destinations: the user configures this endpoint.
 */
@Singleton
public class OllamaClient {
    private static final Logger LOG = LoggerFactory.getLogger(OllamaClient.class);

    private final Json json;
    private final AppConfig config;
    private final HttpClient http;

    public OllamaClient(Json json, AppConfig config) {
        this.json = json;
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public AiStatus status(ModelSettings settings) {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(tagsUri(settings.endpoint()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = Math.round((System.nanoTime() - start) / 1_000_000.0);
            if (response.statusCode() / 100 != 2) {
                return new AiStatus(false, false, settings.endpoint(), settings.model(), "UNAVAILABLE",
                        "Ollama answered with HTTP " + response.statusCode() + ".", latency, TextNow.now(), List.of());
            }
            List<AiModelInfo> models = parseModels(response.body());
            boolean available = models.stream().anyMatch(model -> namesMatch(model.name(), settings.model()));
            String status = available ? "READY" : "MODEL_MISSING";
            String message = available
                    ? "Local model is available."
                    : "Ollama is reachable, but the model '" + settings.model() + "' is not installed. Run: ollama pull " + settings.model();
            return new AiStatus(true, available, settings.endpoint(), settings.model(), status, message,
                    latency, TextNow.now(), models);
        } catch (HttpTimeoutException e) {
            return AiStatus.unavailable(settings.endpoint(), settings.model(),
                    "The local model runtime did not respond within 5 seconds. Start Ollama and try again.");
        } catch (ConnectException e) {
            return AiStatus.unavailable(settings.endpoint(), settings.model(),
                    "Cannot reach Ollama at " + settings.endpoint() + ". Start it with: ollama serve");
        } catch (IOException e) {
            return AiStatus.unavailable(settings.endpoint(), settings.model(),
                    "Could not reach the local model runtime: " + safeMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AiStatus.unavailable(settings.endpoint(), settings.model(), "Model health check was interrupted.");
        }
    }

    public AiTestResult testConnection(ModelSettings settings) {
        long start = System.nanoTime();
        try {
            String response = generate(settings, "You are a local health check. Reply briefly.",
                    "Reply with exactly: GhostShift local model ready", false, 24, false);
            long latency = Math.round((System.nanoTime() - start) / 1_000_000.0);
            return new AiTestResult(true, settings.endpoint(), settings.model(), latency,
                    "The local model answered.", truncate(response, 300));
        } catch (AiUnavailableException | AiInvalidResponseException e) {
            long latency = Math.round((System.nanoTime() - start) / 1_000_000.0);
            return new AiTestResult(false, settings.endpoint(), settings.model(), latency, e.getMessage(), "");
        }
    }

    public String generate(ModelSettings settings, String system, String user, boolean jsonMode,
                           int maxTokens, boolean retryOnce) {
        int attempts = retryOnce ? 2 : 1;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return generateOnce(settings, system, user, jsonMode, maxTokens);
            } catch (AiUnavailableException e) {
                last = e;
                if (attempt < attempts && e.retryable()) {
                    try {
                        Thread.sleep(350);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AiUnavailableException("Model inference was cancelled.", false, interrupted);
                    }
                    continue;
                }
                throw e;
            }
        }
        throw last == null ? new AiUnavailableException("The local model is unavailable.", false, null) : last;
    }

    private String generateOnce(ModelSettings settings, String system, String user, boolean jsonMode, int maxTokens) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", settings.model());
        payload.put("stream", false);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));
        payload.put("options", Map.of(
                "temperature", 0.2,
                "num_predict", maxTokens,
                "num_ctx", 4096
        ));
        if (jsonMode) {
            payload.put("format", "json");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(chatUri(settings.endpoint()))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.write(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new AiUnavailableException("The model '" + settings.model() + "' is not available in Ollama.", false, null);
            }
            if (response.statusCode() / 100 != 2) {
                boolean retryable = response.statusCode() >= 500;
                throw new AiUnavailableException("Ollama returned HTTP " + response.statusCode() + ".", retryable, null);
            }
            Map<String, Object> body = json.readObject(response.body());
            Object message = body.get("message");
            if (!(message instanceof Map<?, ?> messageMap) || !(messageMap.get("content") instanceof String content)) {
                throw new AiInvalidResponseException("The model returned an unexpected response shape.");
            }
            String cleaned = content.strip();
            if (cleaned.isEmpty()) {
                throw new AiInvalidResponseException("The model returned an empty response.");
            }
            return cleaned;
        } catch (HttpTimeoutException e) {
            throw new AiUnavailableException("The local model timed out after " + settings.timeoutSeconds()
                    + " seconds. Try a smaller model or a shorter input.", false, e);
        } catch (ConnectException e) {
            throw new AiUnavailableException("Cannot reach Ollama at " + settings.endpoint()
                    + ". Start it with: ollama serve", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Model inference was cancelled.", false, e);
        } catch (IOException e) {
            throw new AiUnavailableException("The model request failed: " + safeMessage(e), true, e);
        }
    }

    private List<AiModelInfo> parseModels(String body) {
        try {
            Map<String, Object> root = json.readObject(body);
            Object modelsValue = root.get("models");
            List<AiModelInfo> models = new ArrayList<>();
            if (modelsValue instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map && map.get("name") instanceof String name) {
                        long size = map.get("size") instanceof Number n ? n.longValue() : 0L;
                        String parameterSize = "";
                        String quantization = "";
                        String family = "";
                        if (map.get("details") instanceof Map<?, ?> details) {
                            parameterSize = asString(details.get("parameter_size"));
                            quantization = asString(details.get("quantization_level"));
                            family = asString(details.get("family"));
                        }
                        models.add(new AiModelInfo(name, size, parameterSize, quantization, family));
                    }
                }
            }
            return models;
        } catch (RuntimeException e) {
            LOG.warn("Could not parse Ollama model list: {}", e.getMessage());
            return List.of();
        }
    }

    private static boolean namesMatch(String available, String selected) {
        if (available == null || selected == null) {
            return false;
        }
        String a = available.strip();
        String b = selected.strip();
        return a.equals(b) || a.equals(b + ":latest") || b.equals(a + ":latest");
    }

    private static URI tagsUri(String endpoint) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(base + "/api/tags");
    }

    private static URI chatUri(String endpoint) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(base + "/api/chat");
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : "";
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String truncate(String value, int max) {
        return value == null ? "" : value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static final class TextNow {
        private static String now() {
            return Instant.now().toString();
        }
    }

    public static class AiUnavailableException extends RuntimeException {
        private final boolean retryable;

        public AiUnavailableException(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    public static class AiInvalidResponseException extends RuntimeException {
        public AiInvalidResponseException(String message) {
            super(message);
        }
    }
}
