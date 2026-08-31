package gal.subtitula.api.transparency.guide.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public record GuideDecisionResponse(
        UUID id,
        UUID agendaItemId,
        String neutralDescription,
        String status,
        String motion,
        String result,
        JsonNode voteDetails,
        List<GuideEvidenceResponse> evidence,
        long version) {
}
