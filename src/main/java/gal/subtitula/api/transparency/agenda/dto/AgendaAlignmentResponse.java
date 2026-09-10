package gal.subtitula.api.transparency.agenda.dto;

import java.util.UUID;

public record AgendaAlignmentResponse(
        UUID id,
        UUID agendaItemId,
        int occurrence,
        UUID startSegmentId,
        UUID endSegmentId,
        long startMs,
        long endMs,
        String state,
        boolean requiresHumanCheck,
        String algorithmVersion,
        boolean revisited,
        long version) {
}
