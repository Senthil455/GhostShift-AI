package ai.ghostshift.util;

import ai.ghostshift.api.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathGuardTest {
    private final PathGuard guard = new PathGuard();

    @TempDir
    Path root;

    @Test
    void resolvesARegularFileInsideTheRoot() throws IOException {
        Path file = Files.writeString(root.resolve("notes.txt"), "hello");

        Path resolved = guard.resolveExistingFile(root, "notes.txt");

        assertEquals(file.toRealPath(), resolved);
    }

    @Test
    void rejectsParentTraversal() {
        ApiException error = assertThrows(ApiException.class,
                () -> guard.resolveExistingFile(root, "../outside.txt"));

        assertEquals(403, error.status());
    }

    @Test
    void rejectsAbsoluteDestinations() {
        ApiException error = assertThrows(ApiException.class,
                () -> guard.resolveForWrite(root, root.resolve("sub/nested.txt").toString()));

        assertEquals(400, error.status());
    }

    @Test
    void rejectsTraversalInDestinations() {
        ApiException error = assertThrows(ApiException.class,
                () -> guard.resolveForWrite(root, "../elsewhere.txt"));

        assertEquals(403, error.status());
    }

    @Test
    void createsANonCollidingNameWithoutOverwriting() throws IOException {
        Path existing = Files.writeString(root.resolve("report.txt"), "first");
        Path desired = root.resolve("report.txt");

        Path chosen = guard.nonColliding(desired);

        assertEquals("report (1).txt", chosen.getFileName().toString());
        assertTrue(Files.exists(existing));
        assertEquals("first", Files.readString(existing));
    }

    @Test
    void acceptsANestedDestinationThatDoesNotExistYet() {
        Path destination = guard.resolveForWrite(root, "GhostShift Organizer/Documents/report.txt");

        assertTrue(destination.startsWith(root));
        assertEquals("report.txt", destination.getFileName().toString());
    }

    @Test
    void rejectsTheWindowsSystemDirectoryWhenOnWindows() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        assertTrue(guard.isSystemPath(Path.of("C:\\Windows\\System32")));
    }
}
