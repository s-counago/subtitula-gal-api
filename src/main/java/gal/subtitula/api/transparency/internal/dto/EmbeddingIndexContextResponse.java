package gal.subtitula.api.transparency.internal.dto;

import java.util.List;
import java.util.UUID;

public record EmbeddingIndexContextResponse(
        UUID jobId,
        UUID projectId,
        UUID publicationId,
        String modelVersion,
        int dimensions,
        long jobVersion,
        long projectVersion,
        List<Document> documents) {

    public record Document(
            UUID id,
            String contentHash,
            String text) {
    }
}
