package gal.subtitula.api.transparency.capability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server-controlled rollout flags for the institutional transparency vertical slices.
 * Missing values deliberately bind to false so a partial deployment fails closed.
 */
@ConfigurationProperties(prefix = "app.capabilities")
public record TransparencyCapabilities(
        boolean durableInstitutionalUpload,
        boolean normalizedTranscript,
        boolean exceptionReview,
        boolean automaticAgenda,
        boolean structuredGuide,
        boolean publicPublication,
        boolean lexicalSearch,
        boolean hybridSearch) {
}
