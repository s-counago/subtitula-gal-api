package gal.subtitula.api.transparency.agenda.dto;

import java.util.List;
import java.util.UUID;

public record AgendaResponse(
        UUID projectId,
        UUID transcriptRevisionId,
        List<AgendaItemResponse> items,
        List<AgendaAlignmentResponse> alignments,
        int optionalChecks) {
}
