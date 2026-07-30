package gal.subtitula.api.transparency.guide.dto;

import java.util.UUID;

public record GuideEvidenceResponse(
        UUID segmentId,
        long startMs,
        long endMs,
        UUID speakerId,
        String speakerLabel,
        String text,
        String purpose) {
}
