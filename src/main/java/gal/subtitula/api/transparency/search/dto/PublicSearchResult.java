package gal.subtitula.api.transparency.search.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PublicSearchResult(
        UUID searchDocumentId,
        UUID publicationId,
        String publicSlug,
        int publicationVersion,
        String kind,
        UUID sourceEntityId,
        UUID evidenceSegmentId,
        String sessionTitle,
        UUID organizationId,
        String organizationName,
        String sessionBody,
        LocalDate sessionDate,
        String title,
        String excerpt,
        UUID speakerId,
        String speakerLabel,
        UUID agendaItemId,
        String agendaTitle,
        Long startMs,
        Long endMs,
        String documentUrl,
        String evidenceHref,
        List<String> matchReasons) {
}
