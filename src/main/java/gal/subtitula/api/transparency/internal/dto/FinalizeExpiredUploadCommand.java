package gal.subtitula.api.transparency.internal.dto;

public record FinalizeExpiredUploadCommand(
        long expectedIntentVersion,
        long expectedRecordingVersion) {
}
