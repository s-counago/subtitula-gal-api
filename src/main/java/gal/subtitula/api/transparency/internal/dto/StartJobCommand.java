package gal.subtitula.api.transparency.internal.dto;

public record StartJobCommand(long expectedJobVersion, long expectedProjectVersion) {
}
