package gal.subtitula.api.transparency.review.dto;

import gal.subtitula.api.transparency.review.ReviewSession;

import java.time.Instant;
import java.util.UUID;

public record ReviewSessionResponse(
        UUID id,
        UUID projectId,
        UUID revisionId,
        Instant startedAt,
        Instant lastActivityAt,
        long activeDurationMs,
        int resolvedCount,
        int dismissedCount,
        int manualEditCount,
        Instant completedAt,
        long version) {

    public static ReviewSessionResponse from(ReviewSession value) {
        return new ReviewSessionResponse(
            value.getId(),
            value.getProjectId(),
            value.getTranscriptRevisionId(),
            value.getStartedAt(),
            value.getLastActivityAt(),
            value.getActiveDurationMs(),
            value.getResolvedCount(),
            value.getDismissedCount(),
            value.getManualEditCount(),
            value.getCompletedAt(),
            value.getVersion());
    }
}
