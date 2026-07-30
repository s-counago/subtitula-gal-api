package gal.subtitula.api.transparency.review.dto;

import java.util.UUID;

public record CompleteReviewResponse(
        UUID projectId,
        UUID revisionId,
        UUID enrichmentJobId,
        String projectStatus,
        long activeDurationMs,
        int resolvedCount,
        int dismissedCount,
        int manualEditCount) {
}
