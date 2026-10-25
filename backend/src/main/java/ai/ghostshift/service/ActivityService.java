package ai.ghostshift.service;

import ai.ghostshift.db.Database;
import ai.ghostshift.domain.ActivityItem;
import ai.ghostshift.domain.HistoryPage;
import ai.ghostshift.util.Text;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class ActivityService {
    private final Database db;

    public ActivityService(Database db) {
        this.db = db;
    }

    public void record(String kind, String title, String detail, String entityId) {
        db.update("INSERT INTO activity(kind, title, detail, entity_id, at) VALUES (?, ?, ?, ?, ?)",
                kind, Text.truncate(title, 200), Text.truncate(detail, 600), entityId, Text.now());
    }

    public List<ActivityItem> recent(int limit) {
        int bounded = Math.clamp(limit, 1, 100);
        return db.query("""
                        SELECT id, kind, title, detail, entity_id, at
                        FROM activity ORDER BY at DESC, id DESC LIMIT ?
                        """,
                rs -> map(rs), bounded);
    }

    public HistoryPage page(int page, int pageSize, String kind) {
        int safePage = Math.max(1, page);
        int safeSize = Math.clamp(pageSize, 1, 100);
        String where = kind == null || kind.isBlank() ? "" : " WHERE kind = ?";
        Object[] params = kind == null || kind.isBlank()
                ? new Object[]{safeSize, (safePage - 1) * safeSize}
                : new Object[]{kind, safeSize, (safePage - 1) * safeSize};
        String sql = """
                SELECT id, kind, title, detail, entity_id, at
                FROM activity
                """ + where + " ORDER BY at DESC, id DESC LIMIT ? OFFSET ?";
        List<ActivityItem> items = db.query(sql, rs -> map(rs), params);
        long total = db.queryOne("SELECT COUNT(*) FROM activity" + where,
                rs -> rs.getLong(1), kind == null || kind.isBlank() ? new Object[]{} : new Object[]{kind})
                .orElse(0L);
        return new HistoryPage(items, safePage, safeSize, total);
    }

    private static ActivityItem map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ActivityItem(
                rs.getLong("id"),
                rs.getString("kind"),
                rs.getString("title"),
                rs.getString("detail"),
                rs.getString("entity_id"),
                rs.getString("at")
        );
    }
}
