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

    public void requireDurableInstitutionalUpload() {
        require(durableInstitutionalUpload, "durable_institutional_upload");
    }

    public void requireNormalizedTranscript() {
        require(normalizedTranscript, "normalized_transcript");
    }

    public void requireExceptionReview() {
        require(exceptionReview, "exception_review");
    }

    public void requireAutomaticAgenda() {
        require(automaticAgenda, "automatic_agenda");
    }

    public void requireStructuredGuide() {
        require(structuredGuide, "structured_guide");
    }

    public void requireEnrichment() {
        require(automaticAgenda || structuredGuide, "institutional_enrichment");
    }

    public void requirePublicPublication() {
        require(publicPublication, "public_publication");
    }

    public void requireLexicalSearch() {
        require(lexicalSearch, "lexical_search");
    }

    public void requireHybridSearch() {
        require(hybridSearch, "hybrid_search");
    }

    public void requireProcessingStatus() {
        require(
            durableInstitutionalUpload
                || normalizedTranscript
                || exceptionReview
                || automaticAgenda
                || structuredGuide,
            "institutional_processing");
    }

    private static void require(boolean enabled, String capability) {
        if (!enabled) {
            throw new CapabilityDisabledException(capability);
        }
    }
}
