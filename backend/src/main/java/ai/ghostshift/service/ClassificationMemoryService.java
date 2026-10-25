package ai.ghostshift.service;

import ai.ghostshift.db.Database;
import ai.ghostshift.domain.Category;
import ai.ghostshift.domain.Classification;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Explicit feedback memory. This is preference memory, not model fine-tuning.
 * It stores one user-approved pattern -> category rule locally.
 */
@Singleton
public class ClassificationMemoryService {
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final Database db;

    public ClassificationMemoryService(Database db) {
        this.db = db;
    }

    public Optional<Classification> lookup(String fileName) {
        String key = patternFor(fileName);
        return db.queryOne("SELECT category FROM classification_memory WHERE kind = 'NAME' AND match_key = ?",
                rs -> Category.fromModel(rs.getString(1)).orElse(null), key)
                .flatMap(Optional::ofNullable)
                .map(category -> Classification.memory(category,
                        "Remembered from your earlier correction for file names like this."));
    }

    public void remember(String fileName, Category category) {
        String key = patternFor(fileName);
        db.update("""
                        INSERT INTO classification_memory(kind, match_key, category, created_at, updated_at)
                        VALUES ('NAME', ?, ?, ?, ?)
                        ON CONFLICT(kind, match_key) DO UPDATE SET
                            category = excluded.category, updated_at = excluded.updated_at
                        """, key, category.name(), Text.now(), Text.now());
    }

    public int count() {
        return db.queryOne("SELECT COUNT(*) FROM classification_memory", rs -> rs.getInt(1)).orElse(0);
    }

    static String patternFor(String fileName) {
        String base = Text.stripExtension(fileName)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\(\\d+\\)", " ")
                .strip();
        base = DIGITS.matcher(base).replaceAll("#");
        base = base.replaceAll("[^a-z0-9#._ -]", " ").replaceAll("\\s+", " ").strip();
        if (base.length() > 90) {
            base = base.substring(0, 90).strip();
        }
        return base.isBlank() ? "unnamed" : base;
    }
}
