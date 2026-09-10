package gal.subtitula.api.transparency.internal.dto;

public record EmbeddingFailureCommand(
        String workflowInstanceId,
        String errorCode,
        String safeMessage) {
}
