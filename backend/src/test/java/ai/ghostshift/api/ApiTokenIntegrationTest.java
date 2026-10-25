package ai.ghostshift.api;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The API token is optional and disabled by default. This test boots a separate
 * application context with a token configured so the filter cannot silently regress.
 */
@MicronautTest
@io.micronaut.context.annotation.Property(name = "micronaut.server.port", value = "-1")
@io.micronaut.context.annotation.Property(name = "ghostshift.data-dir", value = "build/test-data/token")
@io.micronaut.context.annotation.Property(name = "ghostshift.api-token", value = "integration-secret")
class ApiTokenIntegrationTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void healthStaysOpenButApiRoutesRequireTheConfiguredToken() {
        HttpResponse<String> health = client.toBlocking().exchange(
                HttpRequest.GET("/api/v1/health"), String.class);
        assertEquals(HttpStatus.OK, health.getStatus());

        HttpClientResponseException missing = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(HttpRequest.GET("/api/v1/settings")));
        assertEquals(HttpStatus.UNAUTHORIZED, missing.getStatus());
        assertTrue(missing.getResponse().getBody(String.class).orElse("").contains("UNAUTHORIZED"));

        HttpClientResponseException wrong = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(HttpRequest.GET("/api/v1/settings")
                        .header("X-GhostShift-Token", "wrong-token")));
        assertEquals(HttpStatus.UNAUTHORIZED, wrong.getStatus());

        HttpResponse<String> bearer = client.toBlocking().exchange(
                HttpRequest.GET("/api/v1/settings")
                        .header("Authorization", "Bearer integration-secret"),
                String.class);
        assertEquals(HttpStatus.OK, bearer.getStatus());

        HttpResponse<String> header = client.toBlocking().exchange(
                HttpRequest.GET("/api/v1/settings")
                        .header("X-GhostShift-Token", "integration-secret"),
                String.class);
        assertEquals(HttpStatus.OK, header.getStatus());
    }
}
