package gal.subtitula.api.transparency.publication.dto;

import gal.subtitula.api.transparency.publication.Publication;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record PublicationResponse(
        UUID id,
        UUID projectId,
        String slug,
        int versionNumber,
        String state,
        UUID transcriptRevisionId,
        UUID guideId,
        UUID recordingId,
        UUID indexJobId,
        String correctionNote,
        Instant publishedAt,
        long version) {

    public static PublicationResponse from(Publication value) {
        return from(value, null);
    }

    public static PublicationResponse from(Publication value, UUID indexJobId) {
        return new PublicationResponse(
            value.getId(),
            value.getProjectId(),
            value.getPublicSlug(),
            value.getVersionNumber(),
            value.getState().name().toLowerCase(Locale.ROOT),
            value.getTranscriptRevisionId(),
            value.getGuideId(),
            value.getRecordingId(),
            indexJobId,
            value.getCorrectionNote(),
            value.getPublishedAt(),
            value.getVersion());
    }
}
