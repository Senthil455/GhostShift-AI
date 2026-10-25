package ai.ghostshift.api;

import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.domain.RunEvent;
import ai.ghostshift.domain.WorkflowDetail;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.service.DirectoryService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@io.micronaut.context.annotation.Property(name = "micronaut.server.port", value = "-1")
@io.micronaut.context.annotation.Property(name = "ghostshift.data-dir", value = "build/test-data/sse")
class RunStreamIntegrationTest {
    private static final Set<String> TERMINAL = Set.of(
            "COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED");

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Client("/")
    SseClient sseClient;

    @Inject
    DirectoryService directories;

    @Test
    void eventStreamReplaysDurableEventsAndClosesAfterTerminalStatus() throws Exception {
        RegisteredDirectory directory = directories.list().getFirst();
        WorkflowDetail workflow = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/workflows", Map.of(
                                "name", "SSE stream test",
                                "description", "Verifies the server-sent event endpoint.",
                                "category", "GENERAL",
                                "definition", Map.of(
                                        "schemaVersion", 1,
                                        "name", "SSE stream test",
                                        "description", "Verifies the server-sent event endpoint.",
                                        "requiresApproval", true,
                                        "failurePolicy", "STOP",
                                        "inputs", List.of(Map.of(
                                                "name", "directoryId",
                                                "label", "Folder",
                                                "type", "DIRECTORY",
                                                "required", true,
                                                "description", "Folder to list.")),
                                        "steps", List.of(
                                                Map.of("id", "list", "type", "LIST_FILES",
                                                        "description", "List files.",
                                                        "parameters", Map.of("recursive", false, "maxFiles", 10),
                                                        "enabled", true),
                                                Map.of("id", "gate", "type", "REQUEST_USER_APPROVAL",
                                                        "description", "A gate that dry runs skip.",
                                                        "parameters", Map.of("title", "SSE gate",
                                                                "explanation", "Test only."),
                                                        "enabled", true)))))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                WorkflowDetail.class);

        WorkflowRun run = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/workflows/" + workflow.id() + "/runs", Map.of(
                                "inputs", Map.of("directoryId", directory.id()),
                                "dryRun", true))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                WorkflowRun.class);

        for (int attempt = 0; attempt < 100 && !TERMINAL.contains(run.status()); attempt++) {
            Thread.sleep(100);
            run = client.toBlocking().retrieve(
                    HttpRequest.GET("/api/v1/runs/" + run.id()), WorkflowRun.class);
        }
        assertTrue(TERMINAL.contains(run.status()), "Dry run should finish before the stream is opened.");

        List<Event<RunEvent>> events = Flux.from(sseClient.eventStream(
                        HttpRequest.GET("/api/v1/runs/" + run.id() + "/events/stream?after=0"),
                        RunEvent.class))
                .collectList()
                .block(Duration.ofSeconds(10));

        assertNotNull(events, "The SSE stream should complete after a terminal run.");
        assertFalse(events.isEmpty(), "The SSE stream should replay persisted run events.");
        assertTrue(events.stream().anyMatch(event -> event.getData().message().contains("started")),
                "The stream should include the run-started event.");
        assertTrue(events.stream().allMatch(event -> event.getId() != null),
                "Every SSE frame should carry its durable event id.");
    }
}
