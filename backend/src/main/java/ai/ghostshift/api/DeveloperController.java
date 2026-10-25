package ai.ghostshift.api;

import ai.ghostshift.domain.AnalysisRequest;
import ai.ghostshift.domain.ReportSummary;
import ai.ghostshift.domain.ReportView;
import ai.ghostshift.service.DeveloperService;
import ai.ghostshift.service.ReportService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;

import java.util.List;

@Controller("/api/v1")
public class DeveloperController {
    private final DeveloperService developer;
    private final ReportService reports;

    public DeveloperController(DeveloperService developer, ReportService reports) {
        this.developer = developer;
        this.reports = reports;
    }

    @Post("/developer/analyze")
    public ReportView analyze(@Body AnalysisRequest request) {
        if (request == null) {
            throw ApiException.badRequest("An analysis request is required.");
        }
        return developer.analyze(request.directoryId(), request.relativePath(),
                request.useAi() == null || request.useAi());
    }

    @Get("/reports")
    public List<ReportSummary> list(@QueryValue(defaultValue = "50") int limit) {
        return reports.list(limit);
    }

    @Get("/reports/{id}")
    public ReportView get(@PathVariable String id) {
        return reports.get(id);
    }

    @Get("/reports/{id}/export")
    public HttpResponse<String> export(@PathVariable String id) {
        String fileName = reports.exportFileName(id);
        return HttpResponse.ok(reports.export(id))
                .contentType(MediaType.of("text/markdown"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
    }
}
