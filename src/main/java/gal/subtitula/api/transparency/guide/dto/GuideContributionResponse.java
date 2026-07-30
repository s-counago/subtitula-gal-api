package gal.subtitula.api.transparency.guide.dto;

import java.util.List;
import java.util.UUID;

public record GuideContributionResponse(
        UUID id,
        UUID speakerId,
        String speakerLabel,
        String kind,
        String neutralSummary,
        String generationState,
        List<GuideEvidenceResponse> evidence,
        long version) {
}
