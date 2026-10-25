package ai.ghostshift.util;

import ai.ghostshift.api.ApiException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Filesystem safety boundary. Every read and write passes through here.
 *
 * <p>We resolve real paths (not string prefixes), reject symlinks in the path,
 * reject system roots, and never accept an AI-supplied destination.</p>
 */
@Singleton
public class PathGuard {

    public Path validateRegistration(Path requested) {
        if (requested == null) {
            throw ApiException.badRequest("Choose a directory to register.");
        }
        Path absolute = requested.toAbsolutePath().normalize();
        if (!Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw ApiException.badRequest("That directory does not exist on this computer.");
        }
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw ApiException.badRequest("Select a folder, not a file.");
        }
        Path real;
        try {
            real = absolute.toRealPath();
        } catch (IOException e) {
            throw ApiException.badRequest("The directory could not be resolved: " + e.getMessage());
        }
        if (isSystemPath(real)) {
            throw ApiException.forbidden("System directories cannot be registered. Choose a personal or project folder.");
        }
        if (!Files.isReadable(real)) {
            throw ApiException.forbidden("GhostShift does not have read permission for that folder.");
        }
        return real;
    }

    /** Resolve a user- or DB-supplied path to an existing regular file inside root. */
    public Path resolveExistingFile(Path root, String suppliedPath) {
        if (suppliedPath == null || suppliedPath.isBlank()) {
            throw ApiException.badRequest("A file path is required.");
        }
        Path rootReal = realRoot(root);
        Path input = Path.of(suppliedPath);
        Path candidate = input.isAbsolute() ? input.normalize() : rootReal.resolve(input).normalize();
        ensureInside(rootReal, candidate);
        rejectSymlinkComponents(rootReal, candidate);
        try {
            Path real = candidate.toRealPath();
            ensureInside(rootReal, real);
            if (!Files.isRegularFile(real)) {
                throw ApiException.badRequest("That path is not a regular file.");
            }
            return real;
        } catch (IOException e) {
            throw ApiException.notFound("The file could not be found inside the registered folder.");
        }
    }

    /** Resolve a relative destination inside root without requiring the target to exist. */
    public Path resolveForWrite(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw ApiException.badRequest("A destination path is required.");
        }
        Path rootReal = realRoot(root);
        Path input = Path.of(relativePath);
        if (input.isAbsolute()) {
            throw ApiException.badRequest("Destinations must be relative to the registered folder.");
        }
        Path candidate = rootReal.resolve(input).normalize();
        ensureInside(rootReal, candidate);
        rejectSymlinkComponents(rootReal, candidate);
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(candidate)) {
            throw ApiException.forbidden("GhostShift will not write through a symbolic link.");
        }
        return candidate;
    }

    public void ensureInside(Path root, Path candidate) {
        if (!candidate.normalize().startsWith(root.normalize())) {
            throw ApiException.forbidden("That path is outside the approved directory boundary.");
        }
    }

    public Path realRoot(Path root) {
        try {
            Path real = root.toRealPath();
            if (isSystemPath(real)) {
                throw ApiException.forbidden("System directories cannot be used.");
            }
            return real;
        } catch (IOException e) {
            throw ApiException.conflict("The registered directory is no longer available. Re-register it or restore the folder.");
        }
    }

    public boolean isSystemPath(Path path) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getParent() == null) {
            return true;
        }
        java.util.List<Path> denied;
        if (os.contains("win")) {
            denied = java.util.List.of(
                    Path.of("C:\\Windows"),
                    Path.of("C:\\Program Files"),
                    Path.of("C:\\Program Files (x86)"),
                    Path.of("C:\\ProgramData"),
                    Path.of("C:\\System Volume Information"),
                    Path.of("C:\\$Recycle.Bin"),
                    Path.of("C:\\Recovery")
            );
            String home = System.getProperty("user.home");
            if (home != null && normalized.equals(Path.of(home).toAbsolutePath().normalize())) {
                return true;
            }
        } else {
            denied = java.util.List.of(
                    Path.of("/"), Path.of("/etc"), Path.of("/usr"), Path.of("/bin"),
                    Path.of("/sbin"), Path.of("/lib"), Path.of("/lib64"), Path.of("/boot"),
                    Path.of("/proc"), Path.of("/sys"), Path.of("/dev"), Path.of("/var")
            );
            String home = System.getProperty("user.home");
            if (home != null && normalized.equals(Path.of(home).toAbsolutePath().normalize())) {
                return true;
            }
        }
        for (Path blocked : denied) {
            if (normalized.equals(blocked) || normalized.startsWith(blocked)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSymlink(Path path) {
        return Files.isSymbolicLink(path);
    }

    public void rejectSymlinkComponents(Path root, Path candidate) {
        Path current = root;
        Path relative;
        try {
            relative = root.relativize(candidate);
        } catch (IllegalArgumentException e) {
            throw ApiException.forbidden("That path is outside the approved directory boundary.");
        }
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw ApiException.forbidden("Symbolic links are not followed inside registered folders.");
            }
        }
    }

    /**
     * Returns a non-colliding destination. Never overwrites an existing file.
     * The caller still uses a no-replace move to close the race window.
     */
    public Path nonColliding(Path desired) {
        if (!Files.exists(desired, LinkOption.NOFOLLOW_LINKS)) {
            return desired;
        }
        String fileName = desired.getFileName().toString();
        String base = Text.stripExtension(fileName);
        String ext = Text.extension(fileName);
        for (int i = 1; i < 10_000; i++) {
            Path candidate = desired.resolveSibling(base + " (" + i + ")" + ext);
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return candidate;
            }
        }
        throw ApiException.conflict("Could not find an available file name for " + fileName);
    }
}
