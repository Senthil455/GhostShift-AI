package ai.ghostshift.api;

import ai.ghostshift.config.AppConfig;
import ai.ghostshift.domain.HealthStatus;
import ai.ghostshift.service.DataService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller("/api/v1")
public class SystemController {
    private final DataService data;
    private final AppConfig config;

    public SystemController(DataService data, AppConfig config) {
        this.data = data;
        this.config = config;
    }

    @Get("/health")
    public HealthStatus health() {
        return data.health();
    }

    @Get("/system/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "GhostShift AI");
        info.put("version", AppConfig.VERSION);
        info.put("dataDir", config.dataDir().toString());
        info.put("databasePath", config.databasePath().toString());
        info.put("bind", "local-only by default");
        info.put("apiTokenEnabled", config.apiTokenEnabled());
        info.put("maxScanFiles", config.maxScanFiles());
        info.put("maxScanDepth", config.maxScanDepth());
        info.put("maxLogBytes", config.maxLogBytes());
        return info;
    }
}
