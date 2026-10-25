package ai.ghostshift.api;

import ai.ghostshift.domain.DashboardSummary;
import ai.ghostshift.domain.DuplicateReport;
import ai.ghostshift.domain.HistoryPage;
import ai.ghostshift.domain.TimeSavingsView;
import ai.ghostshift.service.InsightService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;

@Controller("/api/v1")
public class InsightsController {
    private final InsightService insights;

    public InsightsController(InsightService insights) {
        this.insights = insights;
    }

    @Get("/insights/dashboard")
    public DashboardSummary dashboard() {
        return insights.dashboard();
    }

    @Get("/insights/time-savings")
    public TimeSavingsView timeSavings() {
        return insights.timeSavings();
    }

    @Get("/insights/duplicates")
    public DuplicateReport duplicates(@QueryValue long directoryId,
                                      @QueryValue(defaultValue = "false") boolean recursive) {
        return insights.duplicates(directoryId, recursive);
    }

    @Get("/activity")
    public HistoryPage activity(@QueryValue(defaultValue = "1") int page,
                                @QueryValue(defaultValue = "25") int pageSize,
                                @QueryValue(defaultValue = "") String kind) {
        return insights.history(page, pageSize, kind);
    }
}
