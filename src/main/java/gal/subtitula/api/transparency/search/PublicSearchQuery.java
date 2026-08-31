package gal.subtitula.api.transparency.search;

import java.time.LocalDate;
import java.util.UUID;

public record PublicSearchQuery(
        String query,
        String publicSlug,
        UUID organizationId,
        String sessionBody,
        LocalDate dateFrom,
        LocalDate dateTo,
        UUID speakerId,
        UUID agendaItemId,
        String language,
        String kind,
        int limit,
        int offset) {
}
