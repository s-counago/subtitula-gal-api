package gal.subtitula.api.transparency.transcript.dto;

import java.util.UUID;

public record SpeakerResponse(
        UUID id,
        String providerLabel,
        String displayLabel,
        String confirmedName,
        String role,
        String identityState,
        String source,
        long version) {
}
