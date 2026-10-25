package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.util.PathGuard;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only component that mutates user files. It verifies the source has not changed,
 * refuses to overwrite, and records enough recovery metadata for a guarded undo.
 */
@Singleton
public class SafeFileOperations {
    private static final long HASH_LIMIT = 100L * 1024 * 1024;

    private final PathGuard pathGuard;

    public SafeFileOperations(PathGuard pathGuard) {
        this.pathGuard = pathGuard;
    }

    public MoveOutcome moveVerified(Path root, String sourceRelative, String desiredRelative,
                                    long expectedSize, String expectedModifiedAt) {
        Path rootReal = pathGuard.realRoot(root);
        Path source = pathGuard.resolveExistingFile(rootReal, sourceRelative);
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw ApiException.conflict("The source file is no longer available: " + sourceRelative);
        }
        if (!attrs.isRegularFile() || Files.isSymbolicLink(source)) {
            throw ApiException.forbidden("GhostShift will not move symbolic links or special files.");
        }
        if (expectedSize >= 0 && attrs.size() != expectedSize) {
            throw ApiException.conflict("The file changed size after the plan was created. Rescan before moving it.");
        }
        if (expectedModifiedAt != null && !expectedModifiedAt.isBlank()) {
            String current = attrs.lastModifiedTime().toInstant().toString();
            if (!current.equals(expectedModifiedAt)) {
                throw ApiException.conflict("The file was modified after the plan was created. Rescan before moving it.");
            }
        }

        String hash = hashBounded(source);
        Path desired = pathGuard.resolveForWrite(rootReal, desiredRelative);
        Path destination = pathGuard.nonColliding(desired);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw ApiException.conflict("A file already exists at the destination. Nothing was overwritten.");
        }
        try {
            Files.createDirectories(destination.getParent());
            pathGuard.rejectSymlinkComponents(rootReal, destination.getParent());
            pathGuard.ensureInside(rootReal, destination.getParent().toRealPath());
        } catch (IOException e) {
            throw ApiException.conflict("The destination folder could not be prepared: " + e.getMessage());
        }

        try {
            Files.move(source, destination);
        } catch (FileAlreadyExistsException e) {
            destination = pathGuard.nonColliding(desired);
            try {
                Files.move(source, destination);
            } catch (IOException second) {
                throw ApiException.conflict("A destination conflict appeared while moving the file. Nothing was overwritten.");
            }
        } catch (IOException e) {
            throw ApiException.conflict("The file could not be moved: " + e.getMessage());
        }

        String actualRelative = rootReal.relativize(destination).toString().replace('\\', '/');
        String destinationModified;
        try {
            destinationModified = Files.getLastModifiedTime(destination).toInstant().toString();
        } catch (IOException e) {
            destinationModified = "";
        }
        Map<String, Object> recovery = new LinkedHashMap<>();
        recovery.put("originalRelative", sourceRelative);
        recovery.put("movedToRelative", actualRelative);
        recovery.put("sizeBytes", attrs.size());
        recovery.put("sha256", hash);
        recovery.put("destinationModifiedAt", destinationModified);
        recovery.put("movedAt", Text.now());
        return new MoveOutcome(sourceRelative, actualRelative, destination.toAbsolutePath().toString(), recovery);
    }

    /** Restores a moved file only when the destination still matches the recorded fingerprint. */
    public void restoreVerified(Path root, String currentRelative, String originalRelative, Map<String, Object> recovery) {
        Path rootReal = pathGuard.realRoot(root);
        Path current = pathGuard.resolveExistingFile(rootReal, currentRelative);
        Path original = pathGuard.resolveForWrite(rootReal, originalRelative);
        if (Files.exists(original, LinkOption.NOFOLLOW_LINKS)) {
            throw ApiException.conflict("The original location is occupied. GhostShift will not overwrite it.");
        }
        Object sizeValue = recovery.get("sizeBytes");
        Object hashValue = recovery.get("sha256");
        try {
            long size = Files.size(current);
            if (sizeValue instanceof Number number && size != number.longValue()) {
                throw ApiException.conflict("The moved file has changed size since GhostShift moved it. Undo was not performed.");
            }
            if (hashValue instanceof String expectedHash && !expectedHash.isBlank()) {
                String actualHash = hashBounded(current);
                if (!expectedHash.equals(actualHash)) {
                    throw ApiException.conflict("The moved file's contents have changed. Undo was not performed.");
                }
            }
            if (Files.isSymbolicLink(current)) {
                throw ApiException.forbidden("GhostShift will not restore through a symbolic link.");
            }
            Files.createDirectories(original.getParent());
            pathGuard.rejectSymlinkComponents(rootReal, original.getParent());
            pathGuard.ensureInside(rootReal, original.getParent().toRealPath());
            Files.move(current, original);
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw ApiException.conflict("Undo failed: " + e.getMessage());
        }
    }

    public static String hashFile(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hashBounded(Path file) {
        try {
            if (Files.size(file) > HASH_LIMIT) {
                return "";
            }
            return hashFile(file);
        } catch (IOException e) {
            return "";
        }
    }

    public record MoveOutcome(
            String sourceRelative,
            String actualRelative,
            String absoluteDestination,
            Map<String, Object> recovery
    ) {
    }
}
