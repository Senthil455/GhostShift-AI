package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.config.AppConfig;
import ai.ghostshift.domain.FileInfo;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.domain.ScanResult;
import ai.ghostshift.util.PathGuard;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded, read-only directory scanner. It never follows symlinks and never descends
 * into common dependency or version-control directories.
 */
@Singleton
public class FileScanner {
    private static final Logger LOG = LoggerFactory.getLogger(FileScanner.class);
    private static final Set<String> SKIP_DIRECTORIES = Set.of(
            ".git", ".svn", ".hg", "node_modules", "target", "build", "dist", ".gradle",
            "$RECYCLE.BIN", "System Volume Information", "__pycache__", ".venv", "venv"
    );
    public static final String ORGANIZER_FOLDER = "GhostShift Organizer";

    private final AppConfig config;
    private final PathGuard pathGuard;

    public FileScanner(AppConfig config, PathGuard pathGuard) {
        this.config = config;
        this.pathGuard = pathGuard;
    }

    public Scan scan(RegisteredDirectory directory, boolean recursive, int requestedMaxFiles) {
        Path root = pathGuard.realRoot(Path.of(directory.path()));
        int maxFiles = Math.clamp(requestedMaxFiles <= 0 ? config.maxScanFiles() : requestedMaxFiles,
                1, config.maxScanFiles());
        int depth = recursive ? config.maxScanDepth() : 1;
        List<ScannedFile> files = new ArrayList<>();
        int[] skippedSymlinks = {0};
        boolean[] truncated = {false};
        Path dataDir = config.dataDir();
        try {
            Files.walkFileTree(root, Set.of(), depth, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root)) {
                        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                        if (Files.isSymbolicLink(dir) || SKIP_DIRECTORIES.contains(name)
                                || name.startsWith(".") || (name.equals(ORGANIZER_FOLDER) && dir.getParent().equals(root))
                                || dir.toAbsolutePath().normalize().startsWith(dataDir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (files.size() >= maxFiles) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    if (Files.isSymbolicLink(file) || !attrs.isRegularFile()) {
                        if (Files.isSymbolicLink(file)) {
                            skippedSymlinks[0]++;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        pathGuard.ensureInside(root, file.toRealPath());
                        String relative = root.relativize(file).toString().replace('\\', '/');
                        if (relative.isBlank() || relative.startsWith("../")) {
                            return FileVisitResult.CONTINUE;
                        }
                        String name = file.getFileName().toString();
                        String extension = Text.extension(name);
                        String modified = attrs.lastModifiedTime() == null
                                ? Instant.EPOCH.toString()
                                : attrs.lastModifiedTime().toInstant().toString();
                        files.add(new ScannedFile(
                                UUID.randomUUID().toString(),
                                file,
                                relative,
                                name,
                                extension,
                                attrs.size(),
                                modified
                        ));
                    } catch (IOException | RuntimeException e) {
                        LOG.debug("Skipping unreadable file {}: {}", file, e.getMessage());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw ApiException.conflict("GhostShift could not scan that folder: " + e.getMessage());
        }
        files.sort(Comparator.comparing(ScannedFile::name, String.CASE_INSENSITIVE_ORDER));
        if (files.size() >= maxFiles) {
            truncated[0] = true;
        }
        List<FileInfo> views = files.stream().map(ScannedFile::toView).toList();
        return new Scan(root, views, files, truncated[0], skippedSymlinks[0]);
    }

    public ScanResult toResult(RegisteredDirectory directory, Scan scan) {
        return new ScanResult(directory.id(), directory.displayName(), scan.files().size(),
                scan.truncated(), scan.skippedSymlinks(), scan.files());
    }

    public record Scan(Path root, List<FileInfo> files, List<ScannedFile> entries,
                       boolean truncated, int skippedSymlinks) {
    }

    public record ScannedFile(
            String id,
            Path absolutePath,
            String relativePath,
            String name,
            String extension,
            long sizeBytes,
            String modifiedAt
    ) {
        public FileInfo toView() {
            return new FileInfo(id, name, relativePath, extension, sizeBytes, modifiedAt);
        }

        public String normalizedName() {
            return name.toLowerCase(Locale.ROOT);
        }
    }
}
