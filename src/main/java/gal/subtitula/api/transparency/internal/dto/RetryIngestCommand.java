package gal.subtitula.api.transparency.internal.dto;

public record RetryIngestCommand(
        String workflowInstanceId,
        long expectedJobVersion,
        long expectedProjectVersion) {
}
