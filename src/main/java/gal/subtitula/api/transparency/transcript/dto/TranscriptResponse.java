package gal.subtitula.api.transparency.transcript.dto;

import java.util.List;

public record TranscriptResponse(
        TranscriptRevisionResponse revision,
        List<SpeakerResponse> speakers,
        List<EvidenceSegmentResponse> segments,
        boolean legacyAdapter) {
}
