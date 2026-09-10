package gal.subtitula.api.transparency.internal.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HybridSearchCommand(
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
        int offset,
        List<Double> embedding) {
}
