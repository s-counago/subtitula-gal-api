package gal.subtitula.api.transparency.internal.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record IngestTranscriptCommand(
        String provider,
        String model,
        String languageCode,
        String keytermVersion,
        String rawArtifactKey,
        String contentHash,
        long durationMs,
        JsonNode providerUsage,
        Long costMicrounits,
        String costCurrency,
        long expectedJobVersion,
        long expectedProjectVersion,
        List<NormalizedSpeakerCommand> speakers,
        List<NormalizedSegmentCommand> segments) {
}
