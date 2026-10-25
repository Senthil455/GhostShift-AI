package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record HistoryPage(
        List<ActivityItem> items,
        int page,
        int pageSize,
        long total
) {
}
