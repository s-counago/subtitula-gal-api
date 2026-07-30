package gal.subtitula.api.transparency.review.dto;

import java.util.UUID;

public record CompleteReviewRequest(
        UUID reviewSessionId,
        long expectedProjectVersion,
        long expectedRevisionVersion) {
}
