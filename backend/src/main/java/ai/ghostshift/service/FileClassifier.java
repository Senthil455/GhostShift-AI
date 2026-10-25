package ai.ghostshift.service;

import ai.ghostshift.domain.Category;
import ai.ghostshift.domain.Classification;
import jakarta.inject.Singleton;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic rules run before the model. They are fast, explainable, and keep
 * GhostShift useful when Ollama is offline.
 */
@Singleton
public class FileClassifier {
    private static final Map<String, Category> EXTENSIONS = Map.ofEntries(
            Map.entry(".png", Category.IMAGES), Map.entry(".jpg", Category.IMAGES),
            Map.entry(".jpeg", Category.IMAGES), Map.entry(".gif", Category.IMAGES),
            Map.entry(".webp", Category.IMAGES), Map.entry(".svg", Category.IMAGES),
            Map.entry(".heic", Category.IMAGES), Map.entry(".bmp", Category.IMAGES),
            Map.entry(".tif", Category.IMAGES), Map.entry(".tiff", Category.IMAGES),
            Map.entry(".mp4", Category.VIDEOS), Map.entry(".mov", Category.VIDEOS),
            Map.entry(".mkv", Category.VIDEOS), Map.entry(".avi", Category.VIDEOS),
            Map.entry(".webm", Category.VIDEOS),
            Map.entry(".mp3", Category.AUDIO), Map.entry(".wav", Category.AUDIO),
            Map.entry(".flac", Category.AUDIO), Map.entry(".m4a", Category.AUDIO),
            Map.entry(".ogg", Category.AUDIO),
            Map.entry(".zip", Category.ARCHIVES), Map.entry(".rar", Category.ARCHIVES),
            Map.entry(".7z", Category.ARCHIVES), Map.entry(".tar", Category.ARCHIVES),
            Map.entry(".gz", Category.ARCHIVES), Map.entry(".bz2", Category.ARCHIVES),
            Map.entry(".java", Category.SOURCE_CODE), Map.entry(".ts", Category.SOURCE_CODE),
            Map.entry(".tsx", Category.SOURCE_CODE), Map.entry(".js", Category.SOURCE_CODE),
            Map.entry(".jsx", Category.SOURCE_CODE), Map.entry(".py", Category.SOURCE_CODE),
            Map.entry(".rs", Category.SOURCE_CODE), Map.entry(".go", Category.SOURCE_CODE),
            Map.entry(".rb", Category.SOURCE_CODE), Map.entry(".c", Category.SOURCE_CODE),
            Map.entry(".h", Category.SOURCE_CODE), Map.entry(".cpp", Category.SOURCE_CODE),
            Map.entry(".cs", Category.SOURCE_CODE), Map.entry(".kt", Category.SOURCE_CODE),
            Map.entry(".swift", Category.SOURCE_CODE), Map.entry(".vue", Category.SOURCE_CODE),
            Map.entry(".html", Category.SOURCE_CODE), Map.entry(".css", Category.SOURCE_CODE),
            Map.entry(".scss", Category.SOURCE_CODE), Map.entry(".sql", Category.SOURCE_CODE),
            Map.entry(".sh", Category.SOURCE_CODE), Map.entry(".ps1", Category.SOURCE_CODE),
            Map.entry(".log", Category.DEVELOPMENT_LOGS), Map.entry(".out", Category.DEVELOPMENT_LOGS),
            Map.entry(".err", Category.DEVELOPMENT_LOGS), Map.entry(".trace", Category.DEVELOPMENT_LOGS),
            Map.entry(".ppt", Category.PRESENTATIONS), Map.entry(".pptx", Category.PRESENTATIONS),
            Map.entry(".key", Category.PRESENTATIONS), Map.entry(".odp", Category.PRESENTATIONS),
            Map.entry(".yaml", Category.CONFIGURATION), Map.entry(".yml", Category.CONFIGURATION),
            Map.entry(".toml", Category.CONFIGURATION), Map.entry(".ini", Category.CONFIGURATION),
            Map.entry(".env", Category.CONFIGURATION), Map.entry(".properties", Category.CONFIGURATION),
            Map.entry(".conf", Category.CONFIGURATION), Map.entry(".editorconfig", Category.CONFIGURATION)
    );

    private static final Set<String> AMBIGUOUS_EXTENSIONS = Set.of(
            ".pdf", ".txt", ".md", ".markdown", ".doc", ".docx", ".rtf", ".odt",
            ".json", ".csv", ".xml", ".tsv", ""
    );

    private static final Map<String, Category> NAME_HINTS = Map.ofEntries(
            Map.entry("invoice", Category.FINANCIAL_DOCUMENTS),
            Map.entry("receipt", Category.FINANCIAL_DOCUMENTS),
            Map.entry("statement", Category.FINANCIAL_DOCUMENTS),
            Map.entry("payslip", Category.FINANCIAL_DOCUMENTS),
            Map.entry("tax", Category.FINANCIAL_DOCUMENTS),
            Map.entry("budget", Category.FINANCIAL_DOCUMENTS),
            Map.entry("bank", Category.FINANCIAL_DOCUMENTS),
            Map.entry("lecture", Category.STUDY_MATERIALS),
            Map.entry("study", Category.STUDY_MATERIALS),
            Map.entry("chapter", Category.STUDY_MATERIALS),
            Map.entry("homework", Category.STUDY_MATERIALS),
            Map.entry("syllabus", Category.STUDY_MATERIALS),
            Map.entry("exam", Category.STUDY_MATERIALS),
            Map.entry("notes", Category.STUDY_MATERIALS),
            Map.entry("screenshot", Category.IMAGES),
            Map.entry("screen shot", Category.IMAGES),
            Map.entry("photo", Category.IMAGES),
            Map.entry("config", Category.CONFIGURATION),
            Map.entry("settings", Category.CONFIGURATION),
            Map.entry("backup", Category.ARCHIVES),
            Map.entry("archive", Category.ARCHIVES),
            Map.entry("report", Category.DOCUMENTS)
    );

    public Rule classify(String fileName, String extension) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, Category> hint : NAME_HINTS.entrySet()) {
            if (lower.contains(hint.getKey())) {
                return new Rule(hint.getValue(), 0.88,
                        "File name contains \"" + hint.getKey() + "\", which suggests "
                                + hint.getValue().label().toLowerCase(Locale.ROOT) + ".",
                        AMBIGUOUS_EXTENSIONS.contains(ext));
            }
        }
        if (lower.contains("log") && !ext.equals(".log")) {
            return new Rule(Category.DEVELOPMENT_LOGS, 0.82,
                    "File name suggests a log or diagnostic output.", true);
        }
        Category known = EXTENSIONS.get(ext);
        if (known != null) {
            return new Rule(known, 0.92,
                    "Recognized the " + (ext.isBlank() ? "file" : ext) + " extension as "
                            + known.label().toLowerCase(Locale.ROOT) + ".", false);
        }
        if (ext.equals(".json") || ext.equals(".xml")) {
            return new Rule(Category.OTHER, 0.55,
                    "Structured data file; purpose is not clear from the extension alone.", true);
        }
        if (ext.equals(".csv") || ext.equals(".tsv")) {
            return new Rule(Category.OTHER, 0.55,
                    "Tabular data file; purpose is not clear from the name and extension alone.", true);
        }
        if (ext.equals(".pdf") || ext.equals(".doc") || ext.equals(".docx")
                || ext.equals(".txt") || ext.equals(".md") || ext.equals(".rtf") || ext.equals(".odt")) {
            return new Rule(Category.DOCUMENTS, 0.65,
                    "A text or document file. Its purpose needs a closer look.", true);
        }
        if (ext.isBlank()) {
            return new Rule(Category.OTHER, 0.35,
                    "No file extension was available to guide classification.", true);
        }
        return new Rule(Category.OTHER, 0.4,
                "This extension is not in the deterministic rule set.", true);
    }

    public record Rule(Category category, double confidence, String rationale, boolean needsAi) {
    }
}
