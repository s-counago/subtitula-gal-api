package gal.subtitula.api.transparency.internal.dto;

import java.time.Instant;

public record SearchAnalyticsCleanupResponse(
        Instant createdBefore,
        int deletedQueryEvents) {
}
