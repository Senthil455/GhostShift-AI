package ai.ghostshift.service;

import ai.ghostshift.config.AppConfig;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.DemoSeedResult;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.util.PathGuard;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Creates an explicitly synthetic demo folder inside GhostShift's own data
 * directory. It never touches personal files and never fabricates run history.
 */
@Singleton
public class DemoService {
    private static final Logger LOG = LoggerFactory.getLogger(DemoService.class);
    private static final String PREFERENCE = "demo.seeded";

    private final Database db;
    private final AppConfig config;
    private final PathGuard pathGuard;
    private final DirectoryService directories;
    private final WorkflowService workflows;
    private final ActivityService activity;

    public DemoService(Database db, AppConfig config, PathGuard pathGuard, DirectoryService directories,
                       WorkflowService workflows, ActivityService activity) {
        this.db = db;
        this.config = config;
        this.pathGuard = pathGuard;
        this.directories = directories;
        this.workflows = workflows;
        this.activity = activity;
    }

    public DemoSeedResult seed() {
        boolean alreadySeeded = seeded();
        Path inbox = config.demoDir().resolve("inbox");
        int filesCreated = 0;
        try {
            Files.createDirectories(inbox);
            Files.createDirectories(inbox.resolve("duplicates"));
            filesCreated += writeIfMissing(inbox.resolve("README.txt"), """
                    GHOSTSHIFT DEMO FOLDER
                    ======================
                    Everything in this folder is synthetic and was created by GhostShift
                    for demonstration. It contains no personal data.

                    Try "Tidy a folder (example)" to preview and approve safe file moves.
                    Files are moved into "GhostShift Organizer" inside this demo folder.
                    GhostShift never deletes or overwrites a file.
                    """);
            filesCreated += writeIfMissing(inbox.resolve("invoice-2026-03.txt"), """
                    SYNTHETIC INVOICE — DEMO DATA
                    Invoice: DEMO-2026-03
                    Issued: 2026-03-01
                    Amount: 42.00 (fiction)
                    Note: This is not a real financial document.
                    """);
            filesCreated += writeIfMissing(inbox.resolve("meeting-notes.txt"), """
                    SYNTHETIC MEETING NOTES — DEMO DATA
                    Topic: Local-first automation demo
                    Decisions: keep approvals explicit, never delete files.
                    Action: test the example workflows in GhostShift.
                    """);
            filesCreated += writeIfMissing(inbox.resolve("duplicates/meeting-notes-copy.txt"), """
                    SYNTHETIC MEETING NOTES — DEMO DATA
                    Topic: Local-first automation demo
                    Decisions: keep approvals explicit, never delete files.
                    Action: test the example workflows in GhostShift.
                    """);
            filesCreated += writeIfMissing(inbox.resolve("sample-app.log"), """
                    2026-03-10T09:15:01Z INFO  demo-app started synthetic demo mode
                    2026-03-10T09:15:02Z ERROR demo-sync failed: connection refused to localhost:5432
                    2026-03-10T09:15:03Z WARN  demo-sync retry scheduled
                    2026-03-10T09:15:05Z ERROR demo-sync failed: connection refused to localhost:5432
                    2026-03-10T09:15:09Z ERROR request 1043 timed out after 2000 ms
                    2026-03-10T09:15:12Z WARN  demo-app degraded mode
                    2026-03-10T09:15:20Z ERROR demo-sync failed: connection refused to localhost:5432
                    2026-03-10T09:15:21Z INFO  demo-app shutdown requested
                    """);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the demo folder: " + e.getMessage(), e);
        }

        RegisteredDirectory directory = findOrRegister(inbox);
        int workflowsCreated = alreadySeeded ? 0 : workflows.seedExamples();
        if (!alreadySeeded) {
            db.update("""
                    INSERT INTO user_preference(preference_key, value_json, updated_at)
                    VALUES (?, 'true', ?)
                    ON CONFLICT(preference_key) DO UPDATE SET value_json = 'true', updated_at = excluded.updated_at
                    """, PREFERENCE, Text.now());
            activity.record("DEMO", "Demo workspace ready",
                    "Synthetic files and example workflows were created locally.", String.valueOf(directory.id()));
        }
        return new DemoSeedResult(directory.id(), directory.displayName(), filesCreated, workflowsCreated,
                alreadySeeded
                        ? "The demo workspace already existed. No files were overwritten."
                        : "Demo workspace created inside GhostShift's local data folder. All files are synthetic.");
    }

    private RegisteredDirectory findOrRegister(Path inbox) {
        Path real;
        try {
            real = pathGuard.validateRegistration(inbox);
        } catch (RuntimeException e) {
            throw new IllegalStateException("The demo folder is not available: " + e.getMessage(), e);
        }
        List<RegisteredDirectory> existing = directories.list();
        for (RegisteredDirectory directory : existing) {
            try {
                if (Path.of(directory.path()).toAbsolutePath().normalize().equals(real)) {
                    return directory;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return directories.register(inbox.toString(), "GhostShift demo inbox", "GENERAL", true);
    }

    private static int writeIfMissing(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            return 0;
        }
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return 1;
    }

    private boolean seeded() {
        return db.queryOne("SELECT value_json FROM user_preference WHERE preference_key = ?",
                rs -> "true".equals(rs.getString(1)), PREFERENCE).orElse(false);
    }
}
