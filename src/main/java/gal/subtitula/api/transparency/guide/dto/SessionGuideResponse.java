package gal.subtitula.api.transparency.guide.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionGuideResponse(
        UUID id,
        UUID projectId,
        UUID transcriptRevisionId,
        int versionNumber,
        String schemaVersion,
        String state,
        String assistedLabel,
        Instant createdAt,
        int optionalAgendaChecks,
        int candidateDecisionCount,
        List<GuideTopicResponse> topics,
        long version) {
}
