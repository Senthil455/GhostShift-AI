package ai.ghostshift.api;

import ai.ghostshift.config.AppConfig;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import jakarta.inject.Singleton;

/**
 * Optional local API token. It is disabled unless GHOSTSHIFT_API_TOKEN is set, so
 * the default single-user localhost experience needs no secret storage. CORS
 * preflight and health checks stay open; every other API route is checked.
 */
@Singleton
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class ApiTokenFilter {
    private final AppConfig config;

    public ApiTokenFilter(AppConfig config) {
        this.config = config;
    }

    @RequestFilter
    public void filter(HttpRequest<?> request) {
        if (!config.apiTokenEnabled()) {
            return;
        }
        String path = request.getPath();
        if (!path.startsWith("/api/v1/") || path.equals("/api/v1/health")
                || request.getMethod() == HttpMethod.OPTIONS) {
            return;
        }
        String authorization = request.getHeaders().get("Authorization");
        String supplied = null;
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            supplied = authorization.substring(7).strip();
        }
        if (supplied == null || supplied.isBlank()) {
            supplied = request.getHeaders().get("X-GhostShift-Token");
        }
        if (!config.tokenMatches(supplied)) {
            throw ApiException.unauthorized();
        }
    }
}
