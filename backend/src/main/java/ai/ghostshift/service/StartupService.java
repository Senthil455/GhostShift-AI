package ai.ghostshift.service;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup recovery and first-run seeding. Runs can only be QUEUED or RUNNING in
 * memory, so after a restart they are marked failed rather than silently resumed.
 */
@Singleton
public class StartupService implements ApplicationEventListener<StartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(StartupService.class);

    private final RunRepository runs;
    private final WorkflowService workflows;
    private final DemoService demo;

    public StartupService(RunRepository runs, WorkflowService workflows, DemoService demo) {
        this.runs = runs;
        this.workflows = workflows;
        this.demo = demo;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        try {
            int recovered = runs.recoverInterruptedRuns();
            if (recovered > 0) {
                LOG.warn("Recovered {} interrupted workflow run(s) after restart.", recovered);
            }
            int examples = workflows.seedExamples();
            if (examples > 0) {
                LOG.info("Added {} example workflow(s).", examples);
            }
            demo.seed();
        } catch (RuntimeException e) {
            // Startup must not be blocked by demo data; the API remains available.
            LOG.error("Startup recovery or demo seeding failed safely.", e);
        }
    }
}
