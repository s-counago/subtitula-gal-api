package gal.subtitula.api.transparency.internal.dto;

public record WebhookReceivedCommand(
        String providerRequestId,
        String payloadDigest,
        String artifactKey,
        String workflowInstanceId) {
}
