package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record EmbeddingStartResponse(
        UUID jobId,
        UUID projectId,
        UUID publicationId,
        String state,
        String stage,
        long jobVersion,
        long projectVersion) {
}
