package ai.ghostshift.api;

import ai.ghostshift.domain.RunEvent;
import ai.ghostshift.domain.RunSummary;
import ai.ghostshift.domain.WorkflowRun;
import ai.ghostshift.service.RunService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.sse.Event;
import reactor.core.publisher.Flux;

import java.util.List;

@Controller("/api/v1/runs")
public class RunController {
    private final RunService runs;

    public RunController(RunService runs) {
        this.runs = runs;
    }

    @Get
    public List<RunSummary> list(@QueryValue(defaultValue = "25") int limit) {
        return runs.list(limit);
    }

    @Get("/{runId}")
    public WorkflowRun get(@PathVariable String runId) {
        return runs.get(runId);
    }

    @Get("/{runId}/events")
    public List<RunEvent> events(@PathVariable String runId,
                                 @QueryValue(defaultValue = "0") long after) {
        return runs.events(runId, after);
    }

    @Get("/{runId}/events/stream")
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<Event<RunEvent>> stream(@PathVariable String runId,
                                        @QueryValue(defaultValue = "0") long after) {
        return runs.stream(runId, after);
    }

    @Post("/{runId}/cancel")
    public WorkflowRun cancel(@PathVariable String runId) {
        return runs.cancel(runId);
    }
}
