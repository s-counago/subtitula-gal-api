package gal.subtitula.api.transparency.review.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ReviewIssueResponse(
        UUID id,
        String type,
        String severity,
        String state,
        String resolution,
        UUID segmentId,
        UUID speakerId,
        Long startMs,
        Long endMs,
        String text,
        String speakerLabel,
        JsonNode signals,
        long version) {
}
