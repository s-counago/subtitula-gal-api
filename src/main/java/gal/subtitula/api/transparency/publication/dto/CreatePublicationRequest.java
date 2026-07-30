package gal.subtitula.api.transparency.publication.dto;

import java.util.List;
import java.util.UUID;

public record CreatePublicationRequest(
        UUID recordingId,
        List<UUID> documentIds,
        String desiredSlug,
        String correctionNote,
        long expectedProjectVersion) {
}
