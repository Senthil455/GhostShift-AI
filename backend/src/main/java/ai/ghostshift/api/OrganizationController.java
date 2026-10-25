package ai.ghostshift.api;

import ai.ghostshift.domain.OrganizationPlan;
import ai.ghostshift.domain.OrganizationPlanRequest;
import ai.ghostshift.domain.OrganizationPlanSummary;
import ai.ghostshift.domain.PlanDecisionRequest;
import ai.ghostshift.domain.PlanPatchRequest;
import ai.ghostshift.service.OrganizationService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;

import java.util.List;

@Controller("/api/v1/organization/plans")
public class OrganizationController {
    private final OrganizationService organization;

    public OrganizationController(OrganizationService organization) {
        this.organization = organization;
    }

    @Get
    public List<OrganizationPlanSummary> list(@QueryValue(defaultValue = "25") int limit) {
        return organization.listPlans(limit);
    }

    @Post
    public HttpResponse<OrganizationPlan> create(@Body OrganizationPlanRequest request) {
        return HttpResponse.created(organization.createPlan(request));
    }

    @Get("/{planId}")
    public OrganizationPlan get(@PathVariable String planId) {
        return organization.getPlan(planId);
    }

    @Patch("/{planId}/actions/{actionId}")
    public OrganizationPlan patch(@PathVariable String planId, @PathVariable String actionId,
                                  @Body PlanPatchRequest request) {
        return organization.patchAction(planId, actionId, request);
    }

    @Post("/{planId}/decisions")
    public OrganizationPlan decide(@PathVariable String planId, @Body PlanDecisionRequest request) {
        return organization.decide(planId, request);
    }

    @Post("/{planId}/execute")
    public OrganizationPlan execute(@PathVariable String planId) {
        return organization.execute(planId);
    }

    @Post("/{planId}/cancel")
    public OrganizationPlan cancel(@PathVariable String planId) {
        return organization.cancel(planId);
    }

    @Post("/{planId}/undo")
    public OrganizationPlan undo(@PathVariable String planId) {
        return organization.undo(planId);
    }
}
