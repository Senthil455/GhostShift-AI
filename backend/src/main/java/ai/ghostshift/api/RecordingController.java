package ai.ghostshift.api;

import ai.ghostshift.domain.RecordedStepRequest;
import ai.ghostshift.domain.RecordingView;
import ai.ghostshift.domain.SaveRecordingRequest;
import ai.ghostshift.domain.StartRecordingRequest;
import ai.ghostshift.domain.UpdateRecordedStepRequest;
import ai.ghostshift.domain.WorkflowDetail;
import ai.ghostshift.service.RecordingService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;

import java.util.List;

@Controller("/api/v1/recordings")
public class RecordingController {
    private final RecordingService recordings;

    public RecordingController(RecordingService recordings) {
        this.recordings = recordings;
    }

    @Get
    public List<RecordingView> list() {
        return recordings.list();
    }

    @Post
    public HttpResponse<RecordingView> start(@Body StartRecordingRequest request) {
        return HttpResponse.created(recordings.start(request));
    }

    @Get("/{id}")
    public RecordingView get(@PathVariable String id) {
        return recordings.get(id);
    }

    @Post("/{id}/pause")
    public RecordingView pause(@PathVariable String id) {
        return recordings.pause(id);
    }

    @Post("/{id}/resume")
    public RecordingView resume(@PathVariable String id) {
        return recordings.resume(id);
    }

    @Post("/{id}/stop")
    public RecordingView stop(@PathVariable String id) {
        return recordings.stop(id);
    }

    @Delete("/{id}")
    public HttpResponse<Void> delete(@PathVariable String id) {
        recordings.delete(id);
        return HttpResponse.noContent();
    }

    @Post("/{id}/steps")
    public HttpResponse<RecordingView> addStep(@PathVariable String id, @Body RecordedStepRequest request) {
        return HttpResponse.created(recordings.addStep(id, request));
    }

    @Patch("/{id}/steps/{stepId}")
    public RecordingView updateStep(@PathVariable String id, @PathVariable String stepId,
                                    @Body UpdateRecordedStepRequest request) {
        return recordings.updateStep(id, stepId, request);
    }

    @Delete("/{id}/steps/{stepId}")
    public RecordingView deleteStep(@PathVariable String id, @PathVariable String stepId) {
        return recordings.deleteStep(id, stepId);
    }

    @Post("/{id}/save")
    public HttpResponse<WorkflowDetail> save(@PathVariable String id,
                                             @Nullable @Body SaveRecordingRequest request) {
        return HttpResponse.created(recordings.saveAsWorkflow(id, request));
    }
}
