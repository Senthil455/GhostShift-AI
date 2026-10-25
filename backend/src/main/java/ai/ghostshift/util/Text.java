package ai.ghostshift.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

public final class Text {
    private static final DateTimeFormatter HUMAN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private Text() {
    }

    public static String now() {
        return Instant.now().toString();
    }

    public static String humanTime(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return "—";
        }
        try {
            return HUMAN_TIME.format(Instant.parse(isoTimestamp));
        } catch (RuntimeException e) {
            return isoTimestamp;
        }
    }

    public static String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.strip();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, Math.max(0, maxChars)) + "…";
    }

    public static String safeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "file";
        }
        String cleaned = value.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "-")
                .replaceAll("\\s+", " ")
                .strip();
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(0, 120).strip();
        }
        return cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..") ? "file" : cleaned;
    }

    public static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    public static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    public static String normalizeForSignature(String line) {
        return line
                .replaceAll("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?", "<time>")
                .replaceAll("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b", "<id>")
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\b", "<n>")
                .replaceAll("(?:[A-Za-z]:\\\\|/)[^\\s:]+", "<path>")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
