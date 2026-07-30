package gal.subtitula.api.transparency.transcript.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record EvidenceSegmentResponse(
        UUID id,
        int sequence,
        long startMs,
        long endMs,
        UUID speakerId,
        String text,
        JsonNode wordTimings,
        String reviewState,
        JsonNode signals,
        long version) {
}
