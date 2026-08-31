package gal.subtitula.api.transparency.publication.dto;

import java.util.UUID;

public record ReindexPublicationResponse(
        UUID projectId,
        UUID publicationId,
        UUID jobId,
        boolean semanticIndexRequired) {
}
