package ai.ghostshift.api;

import ai.ghostshift.ai.GhostBrainService;
import ai.ghostshift.domain.AiConnectionRequest;
import ai.ghostshift.domain.AiStatus;
import ai.ghostshift.domain.AiTestResult;
import ai.ghostshift.domain.AppSettings;
import ai.ghostshift.domain.SettingsUpdateRequest;
import ai.ghostshift.service.SettingsService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;

@Controller("/api/v1")
public class SettingsController {
    private final SettingsService settings;
    private final GhostBrainService brain;

    public SettingsController(SettingsService settings, GhostBrainService brain) {
        this.settings = settings;
        this.brain = brain;
    }

    @Get("/settings")
    public AppSettings get() {
        return settings.settings();
    }

    @Patch("/settings")
    public AppSettings update(@Body SettingsUpdateRequest request) {
        return settings.update(request);
    }

    @Post("/settings/model/test")
    public AiTestResult testModel(@Nullable @Body AiConnectionRequest request) {
        return brain.testConnection(request);
    }

    @Get("/ai/status")
    public AiStatus aiStatus(@QueryValue(defaultValue = "false") boolean refresh) {
        return brain.status(refresh);
    }
}
