package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.util.PathGuard;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

@Singleton
public class DirectoryService {
    private final Database db;
    private final PathGuard pathGuard;
    private final ActivityService activity;

    public DirectoryService(Database db, PathGuard pathGuard, ActivityService activity) {
        this.db = db;
        this.pathGuard = pathGuard;
        this.activity = activity;
    }

    public RegisteredDirectory register(String rawPath, String displayName, String type, Boolean allowOrganize) {
        if (rawPath == null || rawPath.isBlank()) {
            throw ApiException.badRequest("Choose a folder to register.");
        }
        Path real = pathGuard.validateRegistration(Path.of(rawPath.strip()));
        boolean organise = allowOrganize == null || allowOrganize;
        if (organise && !Files.isWritable(real)) {
            throw ApiException.forbidden("That folder is not writable. Register it as read-only or choose another folder.");
        }
        String name = displayName == null || displayName.isBlank()
                ? real.getFileName() == null ? real.toString() : real.getFileName().toString()
                : Text.truncate(displayName, 100);
        String directoryType = normalizeType(type);
        try {
            long id = db.insert("""
                            INSERT INTO registered_directory
                                (display_name, canonical_path, directory_type, allow_read, allow_organize, active, created_at)
                            VALUES (?, ?, ?, 1, ?, 1, ?)
                            """, name, real.toString(), directoryType, organise, Text.now());
            activity.record("DIRECTORY", "Folder registered", name + " · " + real, String.valueOf(id));
            return get(id);
        } catch (Database.DbException e) {
            if (e.getCause() != null && e.getCause().getMessage() != null
                    && e.getCause().getMessage().toLowerCase(Locale.ROOT).contains("unique")) {
                throw ApiException.conflict("That folder is already registered.");
            }
            throw e;
        }
    }

    public List<RegisteredDirectory> list() {
        return db.query("""
                        SELECT id, display_name, canonical_path, directory_type, allow_read, allow_organize,
                               active, created_at, last_scan_at
                        FROM registered_directory WHERE active = 1 ORDER BY created_at DESC
                        """, this::map);
    }

    public RegisteredDirectory get(long id) {
        return db.queryOne("""
                        SELECT id, display_name, canonical_path, directory_type, allow_read, allow_organize,
                               active, created_at, last_scan_at
                        FROM registered_directory WHERE id = ? AND active = 1
                        """, this::map, id)
                .orElseThrow(() -> ApiException.notFound("That registered folder does not exist."));
    }

    public RegisteredDirectory requireOrganize(long id) {
        RegisteredDirectory directory = get(id);
        if (!directory.allowOrganize()) {
            throw ApiException.forbidden("This folder is registered as read-only.");
        }
        Path path = root(directory);
        if (!Files.isDirectory(path)) {
            throw ApiException.conflict("The folder is no longer available. Re-register it or restore it.");
        }
        return directory;
    }

    public void requireRead(long id) {
        RegisteredDirectory directory = get(id);
        if (!directory.allowRead()) {
            throw ApiException.forbidden("This folder is registered without read permission.");
        }
    }

    public Path root(RegisteredDirectory directory) {
        return pathGuard.realRoot(Path.of(directory.path()));
    }

    public void markScanned(long id) {
        db.update("UPDATE registered_directory SET last_scan_at = ? WHERE id = ?", Text.now(), id);
    }

    public void remove(long id) {
        RegisteredDirectory directory = get(id);
        db.update("DELETE FROM registered_directory WHERE id = ?", id);
        activity.record("DIRECTORY", "Folder removed from GhostShift",
                directory.displayName() + " · files were not deleted", String.valueOf(id));
    }

    private RegisteredDirectory map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RegisteredDirectory(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("canonical_path"),
                rs.getString("directory_type"),
                rs.getInt("allow_read") == 1,
                rs.getInt("allow_organize") == 1,
                rs.getInt("active") == 1,
                rs.getString("created_at"),
                rs.getString("last_scan_at")
        );
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "GENERAL";
        }
        String normalized = type.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DOWNLOADS", "PROJECT", "DOCUMENTS", "GENERAL" -> normalized;
            default -> "GENERAL";
        };
    }
}
