package ai.ghostshift.service;

import ai.ghostshift.config.AppConfig;
import ai.ghostshift.domain.ErrorGroup;
import ai.ghostshift.domain.LogAnalysis;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic log parsing. This works without a model and provides the evidence
 * that the model is allowed to interpret.
 */
@Singleton
public class LogAnalyzer {
    private static final Pattern TIMESTAMP = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)");
    private static final Pattern LEVEL = Pattern.compile("\\b(FATAL|ERROR|SEVERE|WARN(?:ING)?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCEPTION = Pattern.compile(
            "(?i)(exception|error|failed|failure|fatal|refused|timed out|timeout|denied|unreachable|out of memory)");

    private final AppConfig config;

    public LogAnalyzer(AppConfig config) {
        this.config = config;
    }

    public LogAnalysis analyze(Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("The log file could not be read: " + e.getMessage());
        }
        int maxBytes = config.maxLogBytes();
        boolean truncated = size > maxBytes;
        String content = readBounded(file, maxBytes, truncated);
        String[] lines = content.split("\\R", -1);
        // split(-1) keeps a trailing empty element when the file ends with a newline.
        int totalLines = content.isEmpty() ? 0 : lines.length;
        if (totalLines > 0 && lines[totalLines - 1].isEmpty()) {
            totalLines--;
        }
        int errorLines = 0;
        int warningLines = 0;
        Map<String, GroupBuilder> groups = new LinkedHashMap<>();
        String lastTimestamp = null;
        String firstTimestamp = null;
        int offset = 0;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            if (raw == null) {
                continue;
            }
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher timestampMatcher = TIMESTAMP.matcher(line);
            String timestamp = timestampMatcher.find() ? timestampMatcher.group(1) : null;
            if (timestamp != null) {
                if (firstTimestamp == null) {
                    firstTimestamp = timestamp;
                }
                lastTimestamp = timestamp;
            }
            Matcher levelMatcher = LEVEL.matcher(line);
            String level = levelMatcher.find() ? levelMatcher.group(1).toUpperCase(Locale.ROOT) : "";
            boolean error = level.equals("ERROR") || level.equals("FATAL") || level.equals("SEVERE");
            boolean warning = level.startsWith("WARN");
            if (warning) {
                warningLines++;
            }
            if (error || (EXCEPTION.matcher(line).find() && !isStackFrame(line))) {
                errorLines++;
                String signature = signature(line);
                GroupBuilder builder = groups.computeIfAbsent(signature, key -> new GroupBuilder());
                builder.count++;
                if (builder.sample == null) {
                    builder.sample = Text.truncate(line, 700);
                }
                if (builder.lineNumbers.size() < 20) {
                    builder.lineNumbers.add(truncated ? i + 1 : i + 1);
                }
                if (timestamp != null) {
                    if (builder.firstSeen == null) {
                        builder.firstSeen = timestamp;
                    }
                    builder.lastSeen = timestamp;
                }
            }
        }
        List<ErrorGroup> resultGroups = groups.entrySet().stream()
                .map(entry -> new ErrorGroup(
                        entry.getKey(),
                        entry.getValue().sample,
                        entry.getValue().count,
                        entry.getValue().firstSeen,
                        entry.getValue().lastSeen,
                        entry.getValue().lineNumbers))
                .sorted(Comparator.comparingInt(ErrorGroup::count).reversed())
                .limit(60)
                .toList();
        List<String> observations = new ArrayList<>();
        observations.add(totalLines + " line(s) read" + (truncated
                ? " from the tail of a larger file (line numbers are relative to the tail)."
                : "."));
        observations.add(errorLines + " error line(s), " + warningLines + " warning line(s), "
                + resultGroups.size() + " distinct error signature(s).");
        if (!resultGroups.isEmpty()) {
            ErrorGroup top = resultGroups.getFirst();
            observations.add("The most frequent signature appeared " + top.count()
                    + " time(s): " + Text.truncate(top.sample(), 300));
        }
        if (firstTimestamp != null) {
            observations.add("Observed timestamps span " + firstTimestamp + " to " + lastTimestamp + ".");
        }
        if (errorLines == 0) {
            observations.add("No explicit ERROR/FATAL entries were detected. The file may not be a log, or it may use a different format.");
        }
        return new LogAnalysis(file.getFileName().toString(), totalLines, errorLines, warningLines,
                Math.min(size, maxBytes), truncated, resultGroups,
                firstTimestamp == null ? null : firstTimestamp + " → " + lastTimestamp, observations);
    }

    private static boolean isStackFrame(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("at ") || trimmed.startsWith("Caused by:") || trimmed.startsWith("...");
    }

    private static String signature(String line) {
        String normalized = Text.normalizeForSignature(line);
        // Drop an initial log level/timestamp noise so related messages cluster.
        return Text.truncate(normalized, 500);
    }

    private static String readBounded(Path file, int maxBytes, boolean truncated) {
        try {
            if (!truncated) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                raf.seek(raf.length() - maxBytes);
                byte[] bytes = new byte[maxBytes];
                int read = raf.read(bytes);
                return new String(bytes, 0, Math.max(0, read), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("The log file could not be read: " + e.getMessage());
        }
    }

    private static final class GroupBuilder {
        int count;
        String sample;
        String firstSeen;
        String lastSeen;
        final List<Integer> lineNumbers = new ArrayList<>();
    }
}
