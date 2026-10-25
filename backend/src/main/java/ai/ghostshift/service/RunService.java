package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.domain.RunEvent;
import ai.ghostshift.domain.RunSummary;
import ai.ghostshift.domain.WorkflowDefinition;
import ai.ghostshift.domain.WorkflowDetail;
import ai.ghostshift.domain.WorkflowInput;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.domain.WorkflowRunRequest;
import ai.ghostshift.domain.WorkflowStep;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.Text;
import ai.ghostshift.workflow.WorkflowContext;
import io.micronaut.http.sse.Event;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Starts, observes, and cancels workflow runs. Execution itself lives in RunExecutor. */
@Singleton
public class RunService {
    private static final Set<String> TERMINAL = Set.of(
            "COMPLETED", "PARTIALLY_COMPLETED", "FAILED", "CANCELLED");

    private final RunRepository runs;
    private final WorkflowService workflows;
    private final DirectoryService directories;
    private final RunExecutor executor;
    private final JobRunner jobs;
    private final ActivityService activity;
    private final Json json;

    public RunService(RunRepository runs, WorkflowService workflows, DirectoryService directories,
                      RunExecutor executor, JobRunner jobs, ActivityService activity, Json json) {
        this.runs = runs;
        this.workflows = workflows;
        this.directories = directories;
        this.executor = executor;
        this.jobs = jobs;
        this.activity = activity;
        this.json = json;
    }

    public WorkflowRun start(String workflowId, WorkflowRunRequest request) {
        WorkflowDetail workflow = workflows.get(workflowId);
        if (workflow.archived()) {
            throw ApiException.conflict("Restore this workflow before running it.");
        }
        if (!workflow.validation().valid()) {
            throw ApiException.conflict("Fix the workflow validation errors before running it.");
        }
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        if (!dryRun && !workflow.status().equals("APPROVED")) {
            throw ApiException.conflict("Approve this workflow before running it. "
                    + "Dry runs are available while it is under review.");
        }
        Map<String, Object> inputs = validateInputs(workflow.definition(),
                request == null || request.inputs() == null ? Map.of() : request.inputs(), dryRun);
        Long directoryId = asLong(inputs.get("directoryId"));
        String directoryName = null;
        if (directoryId != null) {
            var directory = directories.get(directoryId);
            directoryName = directory.displayName();
        }
        String runId = UUID.randomUUID().toString();
        String inputsJson = json.write(inputs);
        if (inputsJson.length() > 30_000) {
            throw ApiException.badRequest("The workflow inputs are too large.");
        }
        runs.createRun(runId, workflow.id(), workflow.name(), workflow.currentVersion(), dryRun, inputsJson);
        int index = 0;
        for (WorkflowStep step : workflow.definition().steps()) {
            index++;
            runs.createAction(runId, step.id(), index, step.type(), step.parameters());
        }
        WorkflowContext context = WorkflowContext.empty(directoryId, directoryName);
        runs.saveContext(runId, json.write(context));
        runs.addEvent(runId, "INFO", dryRun
                ? "Dry run queued. No user files will be changed."
                : "Run queued. Waiting for the local workflow worker.", Map.of("dryRun", dryRun));
        activity.record("WORKFLOW", dryRun ? "Workflow dry run queued" : "Workflow run queued",
                workflow.name() + " · version " + workflow.currentVersion(), runId);
        jobs.submit("workflow run " + runId, () -> executor.execute(runId));
        return runs.get(runId);
    }

    public WorkflowRun get(String runId) {
        return runs.get(runId);
    }

    public List<RunSummary> list(int limit) {
        return runs.listSummaries(limit);
    }

    public List<RunEvent> events(String runId, long afterId) {
        runs.getRow(runId);
        return runs.events(runId, afterId);
    }

    /**
     * Server-sent event stream over the same durable run events used by polling.
     * Existing events are replayed from {@code afterId}, then the stream tails the
     * run and closes once the run reaches a terminal status. The browser UI still
     * polls so an optional API token can be held in memory instead of a URL.
     */
    public Flux<Event<RunEvent>> stream(String runId, long afterId) {
        runs.getRow(runId);
        long start = Math.max(0, afterId);
        return Flux.defer(() -> {
            AtomicLong cursor = new AtomicLong(start);
            AtomicBoolean finished = new AtomicBoolean(false);
            return Flux.interval(Duration.ZERO, Duration.ofMillis(500))
                    .takeUntil(tick -> finished.get())
                    .concatMapIterable(tick -> {
                        List<RunEvent> batch = runs.events(runId, cursor.get());
                        if (!batch.isEmpty()) {
                            cursor.set(batch.getLast().id());
                        }
                        if (TERMINAL.contains(runs.getRow(runId).status())) {
                            finished.set(true);
                        }
                        return batch;
                    })
                    .map(event -> Event.of(event).id(Long.toString(event.id())));
        });
    }

    public WorkflowRun cancel(String runId) {
        WorkflowRun run = runs.get(runId);
        if (TERMINAL.contains(run.status())) {
            return run;
        }
        runs.requestCancel(runId);
        if (run.status().equals("WAITING_FOR_APPROVAL")) {
            runs.expireApprovals(runId);
            runs.markRemainingCancelled(runId);
            runs.finalizeRun(runId, "CANCELLED", "Cancelled by the user while waiting for approval.",
                    runs.duration(runId));
            runs.addEvent(runId, "WARN", "Run cancelled while waiting for approval. Nothing else will run.", Map.of());
            activity.record("WORKFLOW", "Workflow run cancelled",
                    run.workflowName() + " · cancelled while waiting for approval", runId);
        } else {
            runs.addEvent(runId, "WARN", "Cancellation requested. The current safe action may finish first.", Map.of());
        }
        return runs.get(runId);
    }

    private Map<String, Object> validateInputs(WorkflowDefinition definition, Map<String, Object> supplied,
                                               boolean dryRun) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        List<WorkflowInput> declared = definition.inputs() == null ? List.of() : definition.inputs();
        Set<String> allowed = new java.util.LinkedHashSet<>();
        for (WorkflowInput input : declared) {
            allowed.add(input.name());
        }
        for (String key : supplied.keySet()) {
            if (!allowed.contains(key)) {
                throw ApiException.badRequest("Unknown workflow input: " + Text.truncate(key, 60));
            }
        }
        for (WorkflowInput input : declared) {
            Object value = supplied.get(input.name());
            if (value == null || (value instanceof String text && text.isBlank())) {
                if (input.required()) {
                    throw ApiException.badRequest("The input '" + input.name() + "' is required.");
                }
                continue;
            }
            switch (input.type()) {
                case "DIRECTORY" -> {
                    Long id = asLong(value);
                    if (id == null || id <= 0) {
                        throw ApiException.badRequest("The input '" + input.name() + "' must be a registered folder id.");
                    }
                    directories.requireRead(id);
                    if (!dryRun && requiresOrganize(definition)) {
                        directories.requireOrganize(id);
                    }
                    inputs.put(input.name(), id);
                }
                case "TEXT" -> {
                    if (!(value instanceof String text)) {
                        throw ApiException.badRequest("The input '" + input.name() + "' must be text.");
                    }
                    if (text.length() > 4000) {
                        throw ApiException.badRequest("The input '" + input.name() + "' is too long.");
                    }
                    inputs.put(input.name(), text);
                }
                case "NUMBER" -> {
                    if (!(value instanceof Number number)) {
                        throw ApiException.badRequest("The input '" + input.name() + "' must be a number.");
                    }
                    inputs.put(input.name(), number);
                }
                case "BOOLEAN" -> {
                    if (!(value instanceof Boolean bool)) {
                        throw ApiException.badRequest("The input '" + input.name() + "' must be true or false.");
                    }
                    inputs.put(input.name(), bool);
                }
                default -> throw ApiException.badRequest("Unsupported input type for '" + input.name() + "'.");
            }
        }
        boolean needsDirectory = definition.steps() != null && definition.steps().stream()
                .filter(WorkflowStep::enabled)
                .map(WorkflowStep::type)
                .anyMatch(type -> Set.of("LIST_FILES", "READ_APPROVED_TEXT", "ANALYZE_LOG", "MOVE_FILE")
                        .contains(type));
        if (needsDirectory && !inputs.containsKey("directoryId")) {
            throw ApiException.badRequest("Choose a registered folder before running this workflow.");
        }
        return inputs;
    }

    private static boolean requiresOrganize(WorkflowDefinition definition) {
        return definition.steps() != null && definition.steps().stream()
                .anyMatch(step -> step.enabled() && "MOVE_FILE".equals(step.type()));
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
