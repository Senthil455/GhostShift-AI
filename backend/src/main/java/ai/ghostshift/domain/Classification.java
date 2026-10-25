package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Classification(
        Category category,
        double confidence,
        String rationale,
        String source
) {
    public static Classification rule(Category category, double confidence, String rationale) {
        return new Classification(category, confidence, rationale, "RULE");
    }

    public static Classification ai(Category category, double confidence, String rationale) {
        return new Classification(category, confidence, rationale, "AI");
    }

    public static Classification memory(Category category, String rationale) {
        return new Classification(category, 0.98, rationale, "MEMORY");
    }

    public static Classification user(Category category) {
        return new Classification(category, 1.0, "Category chosen by the user.", "USER");
    }
}
