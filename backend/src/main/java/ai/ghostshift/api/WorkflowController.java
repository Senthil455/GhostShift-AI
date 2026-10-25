package ai.ghostshift.api;

import ai.ghostshift.domain.AiWorkflowDraft;
import ai.ghostshift.domain.WorkflowActionView;
import ai.ghostshift.domain.WorkflowDefinition;
import ai.ghostshift.domain.WorkflowDetail;
import ai.ghostshift.domain.WorkflowGenerateRequest;
import ai.ghostshift.domain.WorkflowRequest;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.domain.WorkflowRunRequest;
import ai.ghostshift.domain.WorkflowStatusRequest;
import ai.ghostshift.domain.WorkflowSummary;
import ai.ghostshift.domain.WorkflowValidation;
import ai.ghostshift.service.RunService;
import ai.ghostshift.service.WorkflowService;
import ai.ghostshift.workflow.ActionRegistry;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowService workflows;
    private final RunService runs;
    private final ActionRegistry registry;

    public WorkflowController(WorkflowService workflows, RunService runs, ActionRegistry registry) {
        this.workflows = workflows;
        this.runs = runs;
        this.registry = registry;
    }

    @Get
    public List<WorkflowSummary> list(@QueryValue(defaultValue = "false") boolean includeArchived) {
        return workflows.list(includeArchived);
    }

    @Post
    public HttpResponse<WorkflowDetail> create(@Body WorkflowRequest request) {
        return HttpResponse.created(workflows.create(request));
    }

    @Get("/{id}")
    public WorkflowDetail get(@PathVariable String id) {
        return workflows.get(id);
    }

    @Put("/{id}")
    public WorkflowDetail update(@PathVariable String id, @Body WorkflowRequest request) {
        return workflows.update(id, request);
    }

    @Delete("/{id}")
    public WorkflowDetail archive(@PathVariable String id) {
        return workflows.archive(id);
    }

    @Post("/{id}/restore")
    public WorkflowDetail restore(@PathVariable String id) {
        return workflows.restore(id);
    }

    @Patch("/{id}/status")
    public WorkflowDetail status(@PathVariable String id, @Body WorkflowStatusRequest request) {
        return workflows.setStatus(id, request == null ? null : request.status());
    }

    @Post("/validation")
    public WorkflowValidation validate(@Body WorkflowDefinition definition) {
        return workflows.validate(definition);
    }

    @Post("/draft")
    public AiWorkflowDraft draft(@Body WorkflowGenerateRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Describe the workflow you want to draft.");
        }
        return workflows.generateDraft(request.prompt(), request.directoryId());
    }

    @Get("/actions")
    public List<WorkflowActionView> actions() {
        return registry.all().values().stream().map(descriptor -> {
            Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();
            descriptor.parameters().forEach((name, spec) -> {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("type", spec.type());
                detail.put("required", spec.required());
                detail.put("min", spec.min());
                detail.put("max", spec.max());
                parameters.put(name, detail);
            });
            return new WorkflowActionView(descriptor.type(), descriptor.label(), descriptor.risk().name(),
                    descriptor.permission(), descriptor.requiresApproval(), descriptor.description(), parameters);
        }).toList();
    }

    @Post("/{id}/runs")
    public HttpResponse<WorkflowRun> run(@PathVariable String id,
                                         @Nullable @Body WorkflowRunRequest request) {
        return HttpResponse.created(runs.start(id, request));
    }
}
