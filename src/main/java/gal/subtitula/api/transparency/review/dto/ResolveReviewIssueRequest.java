package gal.subtitula.api.transparency.review.dto;

import java.util.UUID;

public record ResolveReviewIssueRequest(
        String resolution,
        long expectedVersion,
        UUID reviewSessionId) {
}
