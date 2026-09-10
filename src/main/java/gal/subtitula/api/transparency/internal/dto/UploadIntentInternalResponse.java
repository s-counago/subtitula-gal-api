package gal.subtitula.api.transparency.internal.dto;

import java.time.Instant;
import java.util.UUID;

public record UploadIntentInternalResponse(
        UUID intentId,
        UUID projectId,
        UUID recordingId,
        UUID jobId,
        String objectKey,
        String mimeType,
        long sizeBytes,
        String checksumSha256,
        Instant expiresAt,
        String state,
        long intentVersion,
        long jobVersion,
        long projectVersion) {
}
