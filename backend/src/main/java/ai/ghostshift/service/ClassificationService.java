package ai.ghostshift.service;

import ai.ghostshift.ai.GhostBrainService;
import ai.ghostshift.ai.OllamaClient;
import ai.ghostshift.domain.AiClassification;
import ai.ghostshift.domain.Category;
import ai.ghostshift.domain.Classification;
import ai.ghostshift.domain.FileInfo;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ClassificationService {
    private static final Logger LOG = LoggerFactory.getLogger(ClassificationService.class);

    private final FileClassifier rules;
    private final ClassificationMemoryService memory;
    private final GhostBrainService brain;

    public ClassificationService(FileClassifier rules, ClassificationMemoryService memory, GhostBrainService brain) {
        this.rules = rules;
        this.memory = memory;
        this.brain = brain;
    }

    public Outcome classify(List<FileScanner.ScannedFile> files, boolean useAi) {
        Map<String, Classification> results = new LinkedHashMap<>();
        List<FileScanner.ScannedFile> ambiguous = new ArrayList<>();

        for (FileScanner.ScannedFile file : files) {
            Classification remembered = memory.lookup(file.name()).orElse(null);
            if (remembered != null) {
                results.put(file.id(), remembered);
                continue;
            }
            FileClassifier.Rule rule = rules.classify(file.name(), file.extension());
            results.put(file.id(), new Classification(rule.category(), rule.confidence(),
                    rule.rationale(), "RULE"));
            if (rule.needsAi()) {
                ambiguous.add(file);
            }
        }

        boolean aiAttempted = useAi && !ambiguous.isEmpty();
        boolean aiApplied = false;
        String warning = null;
        if (aiAttempted) {
            try {
                List<FileInfo> inputs = ambiguous.stream().limit(30).map(FileScanner.ScannedFile::toView).toList();
                List<AiClassification> classified = brain.classifyFiles(inputs);
                Map<String, FileScanner.ScannedFile> byName = new LinkedHashMap<>();
                ambiguous.forEach(file -> byName.put(file.name(), file));
                for (AiClassification item : classified) {
                    FileScanner.ScannedFile file = byName.get(item.fileName());
                    if (file == null) {
                        continue;
                    }
                    results.put(file.id(), Classification.ai(item.category(), item.confidence(),
                            "Local model: " + item.reason()));
                    aiApplied = true;
                }
            } catch (OllamaClient.AiUnavailableException | OllamaClient.AiInvalidResponseException e) {
                warning = "AI classification was not applied: " + e.getMessage()
                        + " Deterministic rules were used instead.";
                LOG.info("AI classification fallback: {}", e.getMessage());
            } catch (RuntimeException e) {
                warning = "AI classification failed safely; deterministic rules were used.";
                LOG.warn("Unexpected AI classification failure", e);
            }
        }

        return new Outcome(results, aiAttempted, aiApplied, warning);
    }

    public Classification classifyOne(String fileName, String extension, boolean useAi) {
        Classification remembered = memory.lookup(fileName).orElse(null);
        if (remembered != null) {
            return remembered;
        }
        FileClassifier.Rule rule = rules.classify(fileName, extension);
        if (useAi && rule.needsAi()) {
            try {
                List<AiClassification> result = brain.classifyFiles(List.of(
                        new FileInfo("one", fileName, fileName, extension, 0,
                                java.time.Instant.EPOCH.toString())));
                if (!result.isEmpty()) {
                    AiClassification item = result.getFirst();
                    return Classification.ai(item.category(), item.confidence(),
                            "Local model: " + item.reason());
                }
            } catch (RuntimeException ignored) {
                // Deterministic fallback below is intentional.
            }
        }
        return new Classification(rule.category(), rule.confidence(), rule.rationale(), "RULE");
    }

    public record Outcome(
            Map<String, Classification> classifications,
            boolean aiAttempted,
            boolean aiApplied,
            String warning
    ) {
        public Classification get(String fileId) {
            return classifications.getOrDefault(fileId,
                    new Classification(Category.OTHER, 0.3, "No classification was produced.", "RULE"));
        }
    }
}
