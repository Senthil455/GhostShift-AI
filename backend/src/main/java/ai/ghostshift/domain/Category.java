package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** File categories are deliberately a closed set. AI cannot invent new ones. */
@Serdeable
public enum Category {
    DOCUMENTS("Documents", "documents"),
    IMAGES("Images", "images"),
    VIDEOS("Videos", "videos"),
    AUDIO("Audio", "audio"),
    SOURCE_CODE("Source code", "source-code"),
    DEVELOPMENT_LOGS("Development logs", "development-logs"),
    ARCHIVES("Archives", "archives"),
    STUDY_MATERIALS("Study materials", "study-materials"),
    FINANCIAL_DOCUMENTS("Financial documents", "financial-documents"),
    PRESENTATIONS("Presentations", "presentations"),
    CONFIGURATION("Configuration", "configuration"),
    OTHER("Other", "other");

    private final String label;
    private final String folder;

    Category(String label, String folder) {
        this.label = label;
        this.folder = folder;
    }

    public String label() {
        return label;
    }

    public String folder() {
        return folder;
    }

    public static Optional<Category> fromModel(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.strip().toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        return Arrays.stream(values())
                .filter(category -> category.name().equals(normalized)
                        || category.label.toUpperCase(Locale.ROOT).replace(' ', '_').equals(normalized))
                .findFirst();
    }
}
