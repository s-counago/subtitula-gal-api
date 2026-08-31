package gal.subtitula.api.transparency.internal.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public record IngestGuideCommand(
        UUID transcriptRevisionId,
        String schemaVersion,
        String generationModel,
        String promptVersion,
        String contentHash,
        String rawArtifactKey,
        JsonNode providerUsage,
        Long costMicrounits,
        String costCurrency,
        long expectedJobVersion,
        long expectedProjectVersion,
        List<Alignment> alignments,
        List<Topic> topics) {

    public record Alignment(
            UUID agendaItemId,
            int occurrence,
            UUID startSegmentId,
            UUID endSegmentId,
            JsonNode signals,
            String state,
            boolean requiresHumanCheck,
            String algorithmVersion,
            boolean revisited) {
    }

    public record Topic(
            UUID id,
            int ordinal,
            String title,
            String neutralSummary,
            JsonNode aliases,
            UUID agendaItemId,
            UUID startSegmentId,
            UUID endSegmentId,
            List<UUID> evidenceSegmentIds,
            List<Contribution> contributions,
            List<Decision> decisions) {
    }

    public record Contribution(
            UUID id,
            int ordinal,
            UUID speakerId,
            String kind,
            String neutralSummary,
            boolean explicitClassification,
            List<UUID> evidenceSegmentIds) {
    }

    public record Decision(
            UUID id,
            int ordinal,
            UUID agendaItemId,
            String neutralDescription,
            String motion,
            String result,
            JsonNode voteDetails,
            List<UUID> evidenceSegmentIds) {
    }
}
