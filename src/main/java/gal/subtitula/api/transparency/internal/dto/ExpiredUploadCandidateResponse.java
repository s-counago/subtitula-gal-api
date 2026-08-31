package gal.subtitula.api.transparency.internal.dto;

import java.time.Instant;
import java.util.UUID;

public record ExpiredUploadCandidateResponse(
        UUID intentId,
        UUID projectId,
        UUID recordingId,
        String objectKey,
        Instant expiresAt,
        long intentVersion,
        long recordingVersion) {
}
