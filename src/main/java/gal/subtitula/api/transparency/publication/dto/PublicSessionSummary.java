package gal.subtitula.api.transparency.publication.dto;

import java.time.Instant;
import java.time.LocalDate;

public record PublicSessionSummary(
        String slug,
        int version,
        String title,
        LocalDate sessionDate,
        String sessionBody,
        String sessionType,
        Instant publishedAt) {
}
