package gal.subtitula.api.transparency.internal.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateUploadIntentCommand(
        String environment,
        UUID projectId,
        UUID intentId,
        UUID recordingId,
        UUID jobId,
        UUID clientRequestId,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String checksumSha256,
        boolean usagePermission,
        Instant expiresAt) {
}
