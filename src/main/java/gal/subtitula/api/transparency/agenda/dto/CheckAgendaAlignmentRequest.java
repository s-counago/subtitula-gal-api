package gal.subtitula.api.transparency.agenda.dto;

import java.util.UUID;

public record CheckAgendaAlignmentRequest(
        String action,
        UUID startSegmentId,
        UUID endSegmentId,
        long expectedVersion) {
}
