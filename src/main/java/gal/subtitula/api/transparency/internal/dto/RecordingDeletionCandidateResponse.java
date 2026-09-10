package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record RecordingDeletionCandidateResponse(
        UUID recordingId,
        String objectKey,
        long recordingVersion) {
}
