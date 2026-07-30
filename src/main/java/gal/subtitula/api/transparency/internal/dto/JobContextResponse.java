package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record JobContextResponse(
        UUID jobId,
        UUID projectId,
        UUID recordingId,
        String objectKey,
        String mimeType,
        long sizeBytes,
        String checksumSha256,
        String languageCode,
        String state,
        String stage,
        long jobVersion,
        long projectVersion) {
}
