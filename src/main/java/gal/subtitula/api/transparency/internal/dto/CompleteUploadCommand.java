package gal.subtitula.api.transparency.internal.dto;

public record CompleteUploadCommand(
        String etag,
        long sizeBytes,
        String mimeType,
        String checksumSha256,
        String workflowInstanceId,
        long expectedIntentVersion) {
}
