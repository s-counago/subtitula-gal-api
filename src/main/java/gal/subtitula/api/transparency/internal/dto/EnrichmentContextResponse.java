package gal.subtitula.api.transparency.internal.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EnrichmentContextResponse(
        UUID jobId,
        UUID projectId,
        UUID transcriptRevisionId,
        String transcriptContentHash,
        String languageCode,
        String projectName,
        LocalDate sessionDate,
        String sessionBody,
        long jobVersion,
        long projectVersion,
        List<Segment> segments,
        List<AgendaItem> agendaItems) {

    public record Segment(
            UUID id,
            int sequence,
            long startMs,
            long endMs,
            UUID speakerId,
            String speakerLabel,
            String text) {
    }

    public record AgendaItem(
            UUID id,
            int ordinal,
            String externalIdentifier,
            String title,
            String description) {
    }
}
