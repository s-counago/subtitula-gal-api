package gal.subtitula.api.transparency.guide.dto;

public record DecisionReviewRequest(
        String action,
        String neutralDescription,
        long expectedVersion) {
}
