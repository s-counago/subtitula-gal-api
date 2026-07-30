package gal.subtitula.api.transparency.internal.dto;

import java.time.Instant;

public record SearchAnalyticsCleanupCommand(Instant createdBefore) {
}
