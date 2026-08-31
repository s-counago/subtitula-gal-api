package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record IngestGuideResponse(
        UUID guideId,
        int topicCount,
        int contributionCount,
        int candidateDecisionCount,
        int optionalAgendaCheckCount,
        boolean duplicate) {
}
