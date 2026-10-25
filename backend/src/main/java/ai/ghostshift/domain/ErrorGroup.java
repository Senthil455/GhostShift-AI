package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record ErrorGroup(
        String signature,
        String sample,
        int count,
        String firstSeen,
        String lastSeen,
        List<Integer> lineNumbers
) {
}
