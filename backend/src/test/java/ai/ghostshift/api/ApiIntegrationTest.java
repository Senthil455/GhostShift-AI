package ai.ghostshift.api;

import ai.ghostshift.domain.Approval;
import ai.ghostshift.domain.HistoryPage;
import ai.ghostshift.domain.OrganizationPlan;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.domain.ScanResult;
import ai.ghostshift.domain.WorkflowDetail;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.domain.WorkflowSummary;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@io.micronaut.context.annotation.Property(name = "micronaut.server.port", value = "-1")
@io.micronaut.context.annotation.Property(name = "ghostshift.data-dir", value = "build/test-data/api")
class ApiIntegrationTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void healthAndSettingsAreAvailable() {
        HttpResponse<String> health = client.toBlocking().exchange(
                HttpRequest.GET("/api/v1/health"), String.class);
        assertEquals(HttpStatus.OK, health.getStatus());
        assertTrue(health.body().contains("\"database\":\"OK\""));
        assertTrue(health.body().contains("\"version\":\"1.0.0\""));

        HttpResponse<String> info = client.toBlocking().exchange(
                HttpRequest.GET("/api/v1/system/info"), String.class);
        assertEquals(HttpStatus.OK, info.getStatus());
        assertTrue(info.body().contains("databasePath"));
    }

    @Test
    void organizationPlanMovesNothingUntilApprovedThenUndoes() throws IOException {
        Path root = Files.createTempDirectory("ghostshift-api-plan-");
        Files.writeString(root.resolve("meeting-notes.txt"), "Synthetic integration test notes.");
        Files.writeString(root.resolve("data.csv"), "a,b\n1,2\n");
        Files.writeString(root.resolve("photo.png"), "not-a-real-image");

        RegisteredDirectory directory = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/directories", Map.of(
                                "path", root.toString(),
                                "displayName", "Integration plan folder",
                                "directoryType", "GENERAL",
                                "allowOrganize", true))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                RegisteredDirectory.class);

        ScanResult scan = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/directories/" + directory.id() + "/scan",
                                Map.of("recursive", false, "useAi", false))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                ScanResult.class);
        assertEquals(3, scan.fileCount());

        OrganizationPlan plan = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/organization/plans", Map.of(
                                "directoryId", directory.id(),
                                "useAi", false,
                                "recursive", false))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                OrganizationPlan.class);
        assertEquals("READY_FOR_REVIEW", plan.status());
        assertEquals(3, plan.actions().size());
        assertTrue(Files.exists(root.resolve("meeting-notes.txt")), "Planning must not move files");

        List<Map<String, Object>> decisions = new ArrayList<>();
        for (var action : plan.actions()) {
            decisions.add(Map.of("actionId", action.id(), "decision", "APPROVE"));
        }
        OrganizationPlan approved = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/organization/plans/" + plan.id() + "/decisions",
                                Map.of("decisions", decisions, "rejectUnreviewed", false))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                OrganizationPlan.class);
        assertEquals("APPROVED", approved.status());

        client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/organization/plans/" + plan.id() + "/execute", Map.of())
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                OrganizationPlan.class);

        OrganizationPlan completed = awaitPlan(plan.id(), 15_000);
        assertTrue(List.of("COMPLETED", "PARTIALLY_COMPLETED").contains(completed.status()),
                () -> "Unexpected plan status: " + completed.status());
        assertFalse(Files.exists(root.resolve("meeting-notes.txt")));
        var moved = completed.actions().stream().filter(action -> action.status().equals("MOVED")).toList();
        assertFalse(moved.isEmpty());
        for (var action : moved) {
            assertTrue(Files.exists(root.resolve(action.destinationRelative())),
                    () -> "Missing destination " + action.destinationRelative());
        }

        OrganizationPlan undone = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/organization/plans/" + plan.id() + "/undo", Map.of())
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                OrganizationPlan.class);
        assertEquals("UNDONE", undone.status());
        assertTrue(Files.exists(root.resolve("meeting-notes.txt")), "Undo must restore original files");
    }

    @Test
    void workflowRequiresHumanApprovalBeforeMovingFiles() throws IOException {
        Path root = Files.createTempDirectory("ghostshift-api-workflow-");
        Files.writeString(root.resolve("report.txt"), "Synthetic workflow test.");
        Files.writeString(root.resolve("screenshot.png"), "not-a-real-image");

        RegisteredDirectory directory = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/directories", Map.of(
                                "path", root.toString(),
                                "displayName", "Integration workflow folder",
                                "directoryType", "GENERAL",
                                "allowOrganize", true))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                RegisteredDirectory.class);

        String workflowName = "Integration safe organize " + UUID.randomUUID();
        WorkflowDetail workflow = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/workflows", Map.of(
                                "name", workflowName,
                                "description", "Created by the integration test.",
                                "category", "TEST",
                                "definition", organizeDefinition()))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                WorkflowDetail.class);
        assertTrue(workflow.validation().valid(),
                () -> "Validation failed: " + String.join("; ", workflow.validation().errors()));

        WorkflowDetail approved = client.toBlocking().retrieve(
                HttpRequest.PATCH("/api/v1/workflows/" + workflow.id() + "/status",
                                Map.of("status", "APPROVED"))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                WorkflowDetail.class);
        assertEquals("APPROVED", approved.status());

        WorkflowRun run = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/workflows/" + workflow.id() + "/runs", Map.of(
                                "inputs", Map.of("directoryId", directory.id()),
                                "dryRun", false))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                WorkflowRun.class);
        assertEquals("QUEUED", run.status());

        WorkflowRun waiting = awaitRun(run.id(), "WAITING_FOR_APPROVAL", 15_000);
        assertEquals("WAITING_FOR_APPROVAL", waiting.status());
        assertTrue(Files.exists(root.resolve("report.txt")), "Nothing may move before approval");

        List<Approval> pending = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/approvals"),
                io.micronaut.core.type.Argument.listOf(Approval.class));
        Approval approval = pending.stream()
                .filter(item -> item.runId().equals(run.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a pending approval for the run"));

        client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/approvals/" + approval.id() + "/approve", Map.of("note", "Integration test"))
                        .contentType(MediaType.APPLICATION_JSON_TYPE),
                WorkflowRun.class);

        WorkflowRun completed = awaitRun(run.id(), "COMPLETED", 20_000);
        assertEquals("COMPLETED", completed.status());
        assertFalse(Files.exists(root.resolve("report.txt")), "Approved workflow must move the file");
        assertTrue(completed.actions().stream().anyMatch(action -> action.actionType().equals("MOVE_FILE")
                && action.status().equals("SUCCEEDED")));

        HistoryPage activity = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/activity?page=1&pageSize=50"),
                HistoryPage.class);
        assertNotNull(activity);
        assertTrue(activity.total() > 0);
    }

    @Test
    void unknownRunReturnsStructuredNotFound() {
        HttpClientResponseException error = assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange(HttpRequest.GET("/api/v1/runs/does-not-exist")));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertTrue(error.getResponse().getBody(String.class).orElse("").contains("NOT_FOUND"));
    }

    @Test
    void exampleWorkflowsAreSeeded() {
        List<WorkflowSummary> workflows = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/workflows?includeArchived=true"),
                io.micronaut.core.type.Argument.listOf(WorkflowSummary.class));

        assertTrue(workflows.stream().anyMatch(workflow -> workflow.name().toLowerCase().contains("tidy")));
    }

    private OrganizationPlan awaitPlan(String planId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        OrganizationPlan plan = null;
        while (System.currentTimeMillis() < deadline) {
            plan = client.toBlocking().retrieve(
                    HttpRequest.GET("/api/v1/organization/plans/" + planId), OrganizationPlan.class);
            if (List.of("COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED").contains(plan.status())) {
                return plan;
            }
            sleep();
        }
        return plan;
    }

    private WorkflowRun awaitRun(String runId, String expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        WorkflowRun run = null;
        while (System.currentTimeMillis() < deadline) {
            run = client.toBlocking().retrieve(
                    HttpRequest.GET("/api/v1/runs/" + runId), WorkflowRun.class);
            if (run.status().equals(expected)) {
                return run;
            }
            if (List.of("FAILED", "CANCELLED", "PARTIALLY_COMPLETED").contains(run.status())) {
                return run;
            }
            sleep();
        }
        return run;
    }

    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> organizeDefinition() {
        List<Map<String, Object>> steps = List.of(
                step("list", "LIST_FILES", Map.of("maxFiles", 20)),
                step("classify", "CLASSIFY_FILES", Map.of("useAi", false)),
                step("propose", "PROPOSE_DESTINATION", Map.of()),
                step("preview", "PREVIEW_FILE_MOVE", Map.of()),
                step("approval", "REQUEST_USER_APPROVAL", Map.of(
                        "title", "Approve integration moves",
                        "explanation", "The integration test will approve this step.")),
                step("move", "MOVE_FILE", Map.of("maxFiles", 20))
        );
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("schemaVersion", 1);
        definition.put("name", "Integration safe organize");
        definition.put("description", "List, classify, preview, approve, move.");
        definition.put("steps", steps);
        definition.put("inputs", List.of(Map.of(
                "name", "directoryId",
                "label", "Folder",
                "type", "DIRECTORY",
                "required", true,
                "description", "Folder to organize")));
        definition.put("requiresApproval", true);
        definition.put("failurePolicy", "STOP");
        return definition;
    }

    private Map<String, Object> step(String id, String type, Map<String, Object> parameters) {
        return Map.of(
                "id", id,
                "type", type,
                "description", type + " step",
                "parameters", parameters,
                "enabled", true);
    }
}
