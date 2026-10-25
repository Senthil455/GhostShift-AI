package ai.ghostshift.service;

import ai.ghostshift.api.ApiException;
import ai.ghostshift.config.AppConfig;
import ai.ghostshift.db.Database;
import ai.ghostshift.domain.LogAnalysis;
import ai.ghostshift.domain.LogEvidence;
import ai.ghostshift.domain.ReportSummary;
import ai.ghostshift.domain.ReportView;
import ai.ghostshift.util.Json;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Singleton
public class ReportService {
    private static final Logger LOG = LoggerFactory.getLogger(ReportService.class);

    private final Database db;
    private final Json json;
    private final AppConfig config;
    private final ActivityService activity;

    public ReportService(Database db, Json json, AppConfig config, ActivityService activity) {
        this.db = db;
        this.json = json;
        this.config = config;
        this.activity = activity;
    }

    public ReportView saveLogReport(String sourceRelative, String title, LogEvidence evidence, String markdown,
                                    boolean aiUsed, String model) {
        String id = UUID.randomUUID().toString();
        String now = Text.now();
        db.update("""
                        INSERT INTO report
                            (id, kind, title, source_relative, content_markdown, evidence_json, ai_used, model, created_at)
                        VALUES (?, 'LOG_ANALYSIS', ?, ?, ?, ?, ?, ?, ?)
                        """, id, Text.truncate(title, 200), sourceRelative, markdown,
                json.write(evidence), aiUsed ? 1 : 0, model, now);
        try {
            Files.writeString(config.reportsDir().resolve(id + ".md"), markdown);
        } catch (IOException e) {
            LOG.warn("Report saved to the database but the Markdown copy failed: {}", e.getMessage());
        }
        activity.record("REPORT", "Developer report generated", title + " · " + sourceRelative, id);
        return get(id);
    }

    public ReportView saveWorkflowReport(String title, String markdown, java.util.Map<String, Object> evidence,
                                         boolean aiUsed, String model) {
        String id = UUID.randomUUID().toString();
        String now = Text.now();
        db.update("""
                        INSERT INTO report
                            (id, kind, title, source_relative, content_markdown, evidence_json, ai_used, model, created_at)
                        VALUES (?, 'WORKFLOW', ?, NULL, ?, ?, ?, ?, ?)
                        """, id, Text.truncate(title, 200), markdown,
                evidence == null ? null : json.write(evidence), aiUsed ? 1 : 0, model, now);
        try {
            Files.writeString(config.reportsDir().resolve(id + ".md"), markdown);
        } catch (IOException e) {
            LOG.warn("Report saved to the database but the Markdown copy failed: {}", e.getMessage());
        }
        activity.record("REPORT", "Workflow report generated", Text.truncate(title, 160), id);
        return get(id);
    }

    public List<ReportSummary> list(int limit) {
        int bounded = Math.clamp(limit, 1, 100);
        return db.query("""
                        SELECT id, kind, title, source_relative, ai_used, model, created_at
                        FROM report ORDER BY created_at DESC LIMIT ?
                        """, rs -> new ReportSummary(
                        rs.getString("id"), rs.getString("kind"), rs.getString("title"),
                        rs.getString("source_relative"), rs.getInt("ai_used") == 1,
                        rs.getString("model"), rs.getString("created_at")), bounded);
    }

    public ReportView get(String id) {
        return db.queryOne("""
                        SELECT id, kind, title, source_relative, content_markdown, evidence_json,
                               ai_used, model, created_at
                        FROM report WHERE id = ?
                        """, rs -> {
                    LogEvidence evidence;
                    try {
                        evidence = json.read(rs.getString("evidence_json"), LogEvidence.class);
                    } catch (RuntimeException e) {
                        evidence = null;
                    }
                    return new ReportView(
                            rs.getString("id"), rs.getString("kind"), rs.getString("title"),
                            rs.getString("source_relative"), rs.getString("content_markdown"),
                            evidence, rs.getInt("ai_used") == 1, rs.getString("model"),
                            rs.getString("created_at"));
                }, id)
                .orElseThrow(() -> ApiException.notFound("That report does not exist."));
    }

    public String export(String id) {
        ReportView report = get(id);
        return report.contentMarkdown();
    }

    public String exportFileName(String id) {
        ReportView report = get(id);
        String base = Text.slug(report.title());
        if (base.isBlank()) {
            base = "ghostshift-report";
        }
        return base + ".md";
    }

    public long count() {
        return db.queryOne("SELECT COUNT(*) FROM report", rs -> rs.getLong(1)).orElse(0L);
    }

    public void deleteAllReports() {
        db.update("DELETE FROM report");
        try {
            if (Files.isDirectory(config.reportsDir())) {
                try (var stream = Files.list(config.reportsDir())) {
                    stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not clear generated report files: {}", e.getMessage());
        }
    }
}
