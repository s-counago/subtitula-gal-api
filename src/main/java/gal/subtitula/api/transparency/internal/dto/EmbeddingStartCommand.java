package gal.subtitula.api.transparency.internal.dto;

public record EmbeddingStartCommand(
        String workflowInstanceId,
        long expectedJobVersion,
        long expectedProjectVersion) {
}
