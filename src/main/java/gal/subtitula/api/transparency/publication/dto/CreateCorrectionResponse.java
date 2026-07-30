package gal.subtitula.api.transparency.publication.dto;

import java.util.UUID;

public record CreateCorrectionResponse(
        UUID projectId,
        UUID transcriptRevisionId,
        int transcriptVersionNumber,
        String projectStatus,
        long projectVersion) {
}
