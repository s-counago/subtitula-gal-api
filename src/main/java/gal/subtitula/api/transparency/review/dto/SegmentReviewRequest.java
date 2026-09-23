package gal.subtitula.api.transparency.review.dto;

import java.util.UUID;

public record SegmentReviewRequest(
        String text,
        UUID speakerId,
        boolean clearSpeaker,
        long expectedVersion,
        UUID reviewSessionId) {
}
