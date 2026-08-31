package gal.subtitula.api.transparency.transcript.dto;

import java.util.UUID;

public record TranscriptRevisionResponse(
        UUID id,
        int number,
        String state,
        String source,
        String language,
        long version) {
}
