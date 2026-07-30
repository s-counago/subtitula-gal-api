package gal.subtitula.api.transparency.review.dto;

import java.util.UUID;

public record SpeakerReviewRequest(
        String confirmedName,
        String role,
        long expectedVersion,
        UUID reviewSessionId) {
}
