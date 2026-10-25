package ai.ghostshift.domain;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SavingsLine(
        String label,
        double manualMinutes,
        double actualMinutes,
        double savedMinutes,
        String basis
) {
}
