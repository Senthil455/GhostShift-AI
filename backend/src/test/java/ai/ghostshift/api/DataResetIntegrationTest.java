package ai.ghostshift.api;

import ai.ghostshift.domain.HistoryPage;
import ai.ghostshift.domain.RegisteredDirectory;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data reset only clears GhostShift's own metadata and generated reports. This test uses
 * a dedicated data directory so clearing activity cannot affect other integration tests.
 */
@MicronautTest
@io.micronaut.context.annotation.Property(name = "micronaut.server.port", value = "-1")
@io.micronaut.context.annotation.Property(name = "ghostshift.data-dir", value = "build/test-data/reset")
class DataResetIntegrationTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void activityResetNeverTouchesUserFiles() throws IOException {
        Path root = Files.createTempDirectory("ghostshift-api-reset-");
        Path userFile = root.resolve("keep-me.txt");
        Files.writeString(userFile, "User data must survive a metadata reset.");

        RegisteredDirectory directory = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/directories", Map.of(
                                "path", root.toString(),
                                "displayName", "Reset safety folder",
                                "directoryType", "GENERAL",
                                "allowOrganize", false))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                RegisteredDirectory.class);
        assertNotNull(directory);

        HistoryPage before = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/activity?page=1&pageSize=100"), HistoryPage.class);
        assertTrue(before.total() > 0, "Registering a folder should create activity");

        HttpResponse<String> reset = client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/data/reset", Map.of(
                                "scope", "ACTIVITY", "confirmation", "DELETE"))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                String.class);
        assertEquals(HttpStatus.OK, reset.getStatus());
        assertTrue(reset.body().contains("Your files were not deleted"));

        HistoryPage after = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/activity?page=1&pageSize=100"), HistoryPage.class);
        assertEquals(1, after.total(),
                "Only the reset's own audit entry should remain after clearing activity");
        assertTrue(after.items().getFirst().title().contains("Local data cleared"));
        assertTrue(Files.exists(userFile), "Reset must never delete or move user files");
        assertEquals("User data must survive a metadata reset.", Files.readString(userFile));
    }
}
