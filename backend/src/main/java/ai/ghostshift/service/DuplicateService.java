package ai.ghostshift.service;

import ai.ghostshift.domain.DuplicateFile;
import ai.ghostshift.domain.DuplicateGroup;
import ai.ghostshift.domain.DuplicateReport;
import ai.ghostshift.domain.FileInfo;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only duplicate reporting. GhostShift never deletes or auto-merges duplicates. */
@Singleton
public class DuplicateService {
    private static final Logger LOG = LoggerFactory.getLogger(DuplicateService.class);
    private static final long HASH_LIMIT = 100L * 1024 * 1024;

    private final DirectoryService directories;
    private final FileScanner scanner;

    public DuplicateService(DirectoryService directories, FileScanner scanner) {
        this.directories = directories;
        this.scanner = scanner;
    }

    public DuplicateReport inspect(long directoryId, boolean recursive) {
        RegisteredDirectory directory = directories.get(directoryId);
        directories.requireRead(directoryId);
        FileScanner.Scan scan = scanner.scan(directory, recursive, 2000);
        Map<Long, List<FileScanner.ScannedFile>> bySize = scan.entries().stream()
                .filter(file -> file.sizeBytes() > 0)
                .collect(Collectors.groupingBy(FileScanner.ScannedFile::sizeBytes, LinkedHashMap::new, Collectors.toList()));

        int hashed = 0;
        int skippedLarge = 0;
        Map<String, List<FileScanner.ScannedFile>> byHash = new LinkedHashMap<>();
        for (List<FileScanner.ScannedFile> sameSize : bySize.values()) {
            if (sameSize.size() < 2) {
                continue;
            }
            for (FileScanner.ScannedFile file : sameSize) {
                if (file.sizeBytes() > HASH_LIMIT) {
                    skippedLarge++;
                    continue;
                }
                try {
                    String hash = SafeFileOperations.hashFile(file.absolutePath());
                    byHash.computeIfAbsent(hash, key -> new ArrayList<>()).add(file);
                    hashed++;
                } catch (IOException e) {
                    LOG.debug("Could not hash {}: {}", file.relativePath(), e.getMessage());
                }
            }
        }
        List<DuplicateGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<FileScanner.ScannedFile>> entry : byHash.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            long size = entry.getValue().getFirst().sizeBytes();
            List<DuplicateFile> files = entry.getValue().stream()
                    .map(file -> new DuplicateFile(file.relativePath(), file.sizeBytes(), file.modifiedAt()))
                    .toList();
            groups.add(new DuplicateGroup("EXACT", "Identical content (SHA-256)",
                    size, files));
        }

        Map<String, List<FileScanner.ScannedFile>> bySimilarName = scan.entries().stream()
                .collect(Collectors.groupingBy(file -> normalizeName(file.name()), LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<FileScanner.ScannedFile>> entry : bySimilarName.entrySet()) {
            if (entry.getValue().size() < 2 || entry.getKey().isBlank()) {
                continue;
            }
            boolean allExact = groups.stream()
                    .anyMatch(group -> group.files().size() == entry.getValue().size());
            List<DuplicateFile> files = entry.getValue().stream()
                    .map(file -> new DuplicateFile(file.relativePath(), file.sizeBytes(), file.modifiedAt()))
                    .toList();
            groups.add(new DuplicateGroup("SIMILAR_NAME",
                    allExact ? "Similar names (content may differ)" : "Similar file names",
                    entry.getValue().getFirst().sizeBytes(), files));
        }
        groups.sort(Comparator.comparing(DuplicateGroup::kind).thenComparing(DuplicateGroup::label));
        String notice = "Duplicate detection is read-only. Similar names are not proof of identical content. "
                + "Large files over 100 MB were skipped.";
        return new DuplicateReport(directoryId, hashed, skippedLarge, groups, notice);
    }

    static String normalizeName(String fileName) {
        String base = Text.stripExtension(fileName).toLowerCase(Locale.ROOT)
                .replaceAll("\\(\\d+\\)", "")
                .replaceAll("\\bcopy\\b", "")
                .replaceAll("\\d+", "")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return base;
    }
}
