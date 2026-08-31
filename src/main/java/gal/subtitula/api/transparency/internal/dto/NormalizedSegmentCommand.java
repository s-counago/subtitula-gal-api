package gal.subtitula.api.transparency.internal.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record NormalizedSegmentCommand(
        int sequence,
        long startMs,
        long endMs,
        String speakerProviderLabel,
        String text,
        JsonNode wordTimings,
        JsonNode signals) {
}
