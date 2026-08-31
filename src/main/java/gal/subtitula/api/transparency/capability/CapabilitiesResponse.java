package gal.subtitula.api.transparency.capability;

public record CapabilitiesResponse(
        String contractVersion,
        boolean durableInstitutionalUpload,
        boolean normalizedTranscript,
        boolean exceptionReview,
        boolean automaticAgenda,
        boolean structuredGuide,
        boolean publicPublication,
        boolean lexicalSearch,
        boolean hybridSearch) {

    static CapabilitiesResponse from(TransparencyCapabilities capabilities) {
        return new CapabilitiesResponse(
            "1.0.0",
            capabilities.durableInstitutionalUpload(),
            capabilities.normalizedTranscript(),
            capabilities.exceptionReview(),
            capabilities.automaticAgenda(),
            capabilities.structuredGuide(),
            capabilities.publicPublication(),
            capabilities.lexicalSearch(),
            capabilities.hybridSearch());
    }
}
