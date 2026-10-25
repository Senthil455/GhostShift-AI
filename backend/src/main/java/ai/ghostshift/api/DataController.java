package ai.ghostshift.api;

import ai.ghostshift.domain.DataExport;
import ai.ghostshift.domain.DeleteDataRequest;
import ai.ghostshift.domain.DemoSeedResult;
import ai.ghostshift.service.DataService;
import ai.ghostshift.service.DemoService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import java.util.Map;

@Controller("/api/v1")
public class DataController {
    private final DataService data;
    private final DemoService demo;

    public DataController(DataService data, DemoService demo) {
        this.data = data;
        this.demo = demo;
    }

    @Post("/demo/seed")
    public DemoSeedResult seedDemo() {
        return demo.seed();
    }

    @Get("/data/export")
    public DataExport export() {
        return data.export();
    }

    @Post("/data/reset")
    public Map<String, Object> reset(@Body DeleteDataRequest request) {
        return data.reset(request);
    }
}
