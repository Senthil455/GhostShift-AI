package ai.ghostshift.service;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Small bounded worker pool for file plans and workflow runs. Requests never block on work. */
@Singleton
public class JobRunner {
    private static final Logger LOG = LoggerFactory.getLogger(JobRunner.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ghostshift-worker");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> LOG.error("Uncaught worker error", e));
        return thread;
    });

    public void submit(String label, Runnable work) {
        executor.submit(() -> {
            try {
                work.run();
            } catch (Exception e) {
                LOG.error("Background job failed: {}", label, e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
