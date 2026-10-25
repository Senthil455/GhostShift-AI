package ai.ghostshift.api;

import ai.ghostshift.domain.ApiError;
import ai.ghostshift.domain.Statuses;

import java.util.Map;
import java.util.UUID;

/** Application-controlled HTTP failures. Messages are user-facing and never contain stack traces. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public ApiError toError(String requestId) {
        return new ApiError(code, getMessage(), details, requestId, java.time.Instant.now().toString());
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }

    public static ApiException badRequest(String message, Map<String, Object> details) {
        return new ApiException(400, "BAD_REQUEST", message, details);
    }

    public static ApiException unauthorized() {
        return new ApiException(401, "UNAUTHORIZED", "A valid API token is required.");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, "FORBIDDEN", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "CONFLICT", message);
    }

    public static ApiException unprocessable(String message) {
        return new ApiException(422, "UNPROCESSABLE", message);
    }

    public static ApiException unprocessable(String message, Map<String, Object> details) {
        return new ApiException(422, "UNPROCESSABLE", message, details);
    }

    public static ApiException serviceUnavailable(String message) {
        return new ApiException(503, "SERVICE_UNAVAILABLE", message);
    }

    public static ApiException internal(String message) {
        return new ApiException(500, "INTERNAL_ERROR", message);
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    public static String riskLabel(Statuses.RiskLevel risk) {
        return risk.name();
    }
}
