package gal.subtitula.api.transparency.publication.dto;

import java.util.List;
import java.util.UUID;

public record PublicationChecklistResponse(
        UUID projectId,
        long projectVersion,
        String projectStatus,
        boolean publishable,
        List<PublicationChecklistItem> items,
        UUID defaultRecordingId,
        UUID transcriptRevisionId,
        UUID guideId) {
}
