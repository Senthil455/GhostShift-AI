package ai.ghostshift.service;

import ai.ghostshift.ai.GhostBrainService;
import ai.ghostshift.ai.OllamaClient;
import ai.ghostshift.api.ApiException;
import ai.ghostshift.domain.AiLogInsight;
import ai.ghostshift.domain.ErrorGroup;
import ai.ghostshift.domain.LogAnalysis;
import ai.ghostshift.domain.LogEvidence;
import ai.ghostshift.domain.RegisteredDirectory;
import ai.ghostshift.domain.ReportView;
import ai.ghostshift.util.PathGuard;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GhostDev: deterministic evidence first, local-model interpretation second, honest
 * fallbacks when the model is unavailable or malformed.
 */
@Singleton
public class DeveloperService {
    private static final Logger LOG = LoggerFactory.getLogger(DeveloperService.class);

    private final DirectoryService directories;
    private final LogAnalyzer analyzer;
    private final GhostBrainService brain;
    private final ReportService reports;
    private final PathGuard pathGuard;

    public DeveloperService(DirectoryService directories, LogAnalyzer analyzer, GhostBrainService brain,
                            ReportService reports, PathGuard pathGuard) {
        this.directories = directories;
        this.analyzer = analyzer;
        this.brain = brain;
        this.reports = reports;
        this.pathGuard = pathGuard;
    }

    public ReportView analyze(long directoryId, String relativePath, boolean useAi) {
        RegisteredDirectory directory = directories.get(directoryId);
        if (!directory.allowRead()) {
            throw ApiException.forbidden("This folder is registered without read permission.");
        }
        Path file = pathGuard.resolveExistingFile(directories.root(directory), relativePath);
        String extension = Text.extension(file.getFileName().toString());
        Set<String> textExtensions = Set.of(".log", ".txt", ".out", ".err", ".trace", ".json", ".csv", ".tsv",
                ".md", ".yaml", ".yml", ".conf", ".properties");
        if (!textExtensions.contains(extension)) {
            throw ApiException.badRequest("Choose a text log, JSON, CSV, or Markdown file. "
                    + "Binary files are not analyzed.");
        }
        LogAnalysis analysis = analyzer.analyze(file);
        String aiNotice = null;
        boolean aiUsed = false;
        List<String> causes;
        List<String> checks;
        String summary;
        String model = null;

        if (analysis.errorLines() == 0) {
            causes = List.of();
            checks = List.of("Confirm this is the correct log file.",
                    "Look for the application's actual error output or a crash dump.");
            summary = "No error signatures were found in the readable portion of this file.";
        } else {
            causes = deterministicCauses(analysis);
            checks = deterministicChecks(analysis);
            summary = "GhostShift found " + analysis.errorLines() + " error line(s) across "
                    + analysis.groups().size() + " grouped signature(s). The most frequent group appeared "
                    + analysis.groups().getFirst().count() + " time(s).";
        }

        if (useAi) {
            try {
                AiLogInsight insight = brain.analyzeLog(analysis, file.getFileName().toString());
                summary = insight.summary();
                causes = insight.possibleCauses().isEmpty() ? causes : insight.possibleCauses();
                checks = insight.recommendedChecks().isEmpty() ? checks : insight.recommendedChecks();
                aiNotice = insight.notice();
                aiUsed = true;
                model = brain.modelName();
            } catch (OllamaClient.AiUnavailableException | OllamaClient.AiInvalidResponseException e) {
                aiNotice = "The local model was not used: " + e.getMessage()
                        + " The report below is deterministic and based only on the observed log evidence.";
                LOG.info("Developer analysis fallback: {}", e.getMessage());
            } catch (RuntimeException e) {
                aiNotice = "The local model failed safely. This report uses deterministic evidence only.";
                LOG.warn("Unexpected model failure in developer analysis", e);
            }
        } else {
            aiNotice = "AI analysis was disabled for this run. The report uses deterministic log evidence only.";
        }

        LogEvidence evidence = new LogEvidence(analysis, analysis.observations(), causes, checks,
                aiNotice, aiUsed);
        String markdown = renderMarkdown(file.getFileName().toString(), analysis, evidence);
        String title = "Incident summary · " + file.getFileName();
        return reports.saveLogReport(relativePath, title, evidence, markdown, aiUsed, model);
    }

    private static List<String> deterministicCauses(LogAnalysis analysis) {
        Set<String> causes = new LinkedHashSet<>();
        for (ErrorGroup group : analysis.groups()) {
            String s = group.sample().toLowerCase(Locale.ROOT);
            if (s.contains("connection refused") || s.contains("unreachable") || s.contains("connect timed out")) {
                causes.add("A dependency (database, API, or network service) may be unavailable.");
            } else if (s.contains("timed out") || s.contains("timeout")) {
                causes.add("An operation exceeded its time budget or the dependency is slow.");
            } else if (s.contains("access denied") || s.contains("permission") || s.contains("forbidden")
                    || s.contains("401") || s.contains("403")) {
                causes.add("Credentials, permissions, or access policy may be blocking the operation.");
            } else if (s.contains("out of memory") || s.contains("heap space")) {
                causes.add("The process may be running out of memory.");
            } else if (s.contains("null") || s.contains("nullpointer")) {
                causes.add("A code path may be reading data that was not initialized.");
            } else if (s.contains("no space") || s.contains("disk full")) {
                causes.add("The storage volume may be full.");
            } else if (s.contains("locked") || s.contains("lock")) {
                causes.add("A resource may be locked by another process or transaction.");
            }
            if (causes.size() >= 4) {
                break;
            }
        }
        if (causes.isEmpty()) {
            causes.add("The log shows a recurring fault, but the evidence does not identify its root cause.");
        }
        return new ArrayList<>(causes);
    }

    private static List<String> deterministicChecks(LogAnalysis analysis) {
        List<String> checks = new ArrayList<>();
        ErrorGroup top = analysis.groups().isEmpty() ? null : analysis.groups().getFirst();
        if (top != null) {
            checks.add("Open the referenced log lines and read the full stack trace for the most frequent signature.");
        }
        checks.add("Reproduce the failure once with debug logging enabled and compare the new evidence.");
        checks.add("Verify the external services and configuration this component depends on.");
        checks.add("Check whether failures cluster around a deployment, restart, or configuration change.");
        checks.add("Do not make destructive changes until the evidence supports a specific hypothesis.");
        return checks;
    }

    private static String renderMarkdown(String fileName, LogAnalysis analysis, LogEvidence evidence) {
        StringBuilder md = new StringBuilder();
        md.append("# Development Incident Summary\n\n");
        md.append("**Source:** `").append(fileName.replace("`", "'")).append("`  \n");
        md.append("**Evidence size:** ").append(analysis.bytesRead()).append(" bytes")
                .append(analysis.truncated() ? " (tail of a larger file)" : "").append("  \n");
        md.append("**AI used:** ").append(evidence.aiUsed() ? "yes" : "no").append("\n\n");
        md.append("## Overview\n\n").append(evidence.analysis().errorLines() > 0
                ? "The log contains repeated error evidence. The findings below separate what was observed from what may explain it."
                : "No explicit error signatures were detected in the readable portion of this file.").append("\n\n");
        md.append("## Observed evidence\n\n");
        if (analysis.groups().isEmpty()) {
            md.append("- No grouped error signatures were found.\n");
        } else {
            for (ErrorGroup group : analysis.groups().stream().limit(10).toList()) {
                md.append("- **").append(group.count()).append(" occurrence(s); lines ")
                        .append(group.lineNumbers().stream().limit(8).map(String::valueOf)
                                .reduce((a, b) -> a + ", " + b).orElse("—"))
                        .append(":** `").append(group.sample().replace("`", "'")).append("`\n");
            }
        }
        md.append('\n');
        if (evidence.possibleCauses() != null && !evidence.possibleCauses().isEmpty()) {
            md.append("## Possible causes (hypotheses, not confirmed)\n\n");
            for (String cause : evidence.possibleCauses()) {
                md.append("- ").append(cause).append('\n');
            }
            md.append('\n');
        }
        if (evidence.recommendedChecks() != null && !evidence.recommendedChecks().isEmpty()) {
            md.append("## Recommended checks\n\n");
            int n = 1;
            for (String check : evidence.recommendedChecks()) {
                md.append(n++).append(". ").append(check).append('\n');
            }
            md.append('\n');
        }
        md.append("## Limitations\n\n");
        md.append("These are diagnostic suggestions, not a confirmed root cause. ");
        if (analysis.truncated()) {
            md.append("Only the tail of the file was read; earlier evidence may be missing. ");
        }
        md.append("No commands were executed and no files were changed.\n");
        if (evidence.aiNotice() != null && !evidence.aiNotice().isBlank()) {
            md.append("\n> ").append(evidence.aiNotice().replace("\n", " ")).append('\n');
        }
        return md.toString();
    }
}
