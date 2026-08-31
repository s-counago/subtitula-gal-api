package gal.subtitula.api.transparency.internal.dto;

public record FailJobCommand(
        String errorCode,
        String safeMessage,
        long expectedJobVersion,
        long expectedProjectVersion) {
}
