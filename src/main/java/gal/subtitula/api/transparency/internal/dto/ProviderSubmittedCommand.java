package gal.subtitula.api.transparency.internal.dto;

public record ProviderSubmittedCommand(
        String providerRequestId,
        long expectedJobVersion) {
}
