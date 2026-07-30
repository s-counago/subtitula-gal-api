package gal.subtitula.api.transparency.guide.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public record GuideTopicResponse(
        UUID id,
        int ordinal,
        String title,
        String neutralSummary,
        JsonNode aliases,
        UUID agendaItemId,
        UUID startSegmentId,
        UUID endSegmentId,
        long startMs,
        long endMs,
        String generationState,
        List<GuideEvidenceResponse> evidence,
        List<GuideContributionResponse> contributions,
        List<GuideDecisionResponse> decisions,
        long version) {
}
