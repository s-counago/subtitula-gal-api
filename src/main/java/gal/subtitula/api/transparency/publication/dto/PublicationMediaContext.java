package gal.subtitula.api.transparency.publication.dto;

public record PublicationMediaContext(
        String objectKey,
        String mimeType,
        long sizeBytes,
        String etag) {
}
