package gal.subtitula.api.transparency.internal.dto;

public record StartEnrichmentCommand(
        String workflowInstanceId,
        long expectedJobVersion,
        long expectedProjectVersion) {
}
