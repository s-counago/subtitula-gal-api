package gal.subtitula.api.transparency.internal.dto;

public record RecordingMediaContextResponse(
        String objectKey,
        String mimeType,
        long sizeBytes,
        String etag) {
}
