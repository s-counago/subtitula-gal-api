package gal.subtitula.api.transparency.publication.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PublicSessionResponse(
        UUID publicationId,
        String slug,
        int publicationVersion,
        String title,
        String languageCode,
        LocalDate sessionDate,
        String sessionBody,
        String location,
        String sessionType,
        String correctionNote,
        Instant publishedAt,
        String mediaUrl,
        List<TranscriptSegment> transcript,
        List<AgendaItem> agenda,
        Guide guide,
        List<Document> documents) {

    public record TranscriptSegment(
            UUID id,
            int sequence,
            long startMs,
            long endMs,
            UUID speakerId,
            String speakerLabel,
            String speakerRole,
            String text) {
    }

    public record AgendaItem(
            UUID id,
            int ordinal,
            String externalIdentifier,
            String title,
            String description,
            List<AgendaOccurrence> occurrences) {
    }

    public record AgendaOccurrence(
            long startMs,
            long endMs,
            boolean revisited) {
    }

    public record Guide(
            String label,
            List<Topic> topics) {
    }

    public record Topic(
            UUID id,
            String title,
            String neutralSummary,
            JsonNode aliases,
            UUID agendaItemId,
            long startMs,
            long endMs,
            List<Evidence> evidence,
            List<Contribution> contributions,
            List<Decision> decisions) {
    }

    public record Contribution(
            UUID id,
            UUID speakerId,
            String speakerLabel,
            String kind,
            String neutralSummary,
            List<Evidence> evidence) {
    }

    public record Decision(
            UUID id,
            String neutralDescription,
            String status,
            String motion,
            String result,
            List<Evidence> evidence) {
    }

    public record Evidence(
            UUID segmentId,
            long startMs,
            long endMs,
            String speakerLabel,
            String text) {
    }

    public record Document(
            UUID id,
            String type,
            String title,
            String officialUrl,
            String issuingBody,
            LocalDate documentDate) {
    }
}
