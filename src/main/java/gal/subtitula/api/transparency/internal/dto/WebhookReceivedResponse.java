package gal.subtitula.api.transparency.internal.dto;

public record WebhookReceivedResponse(
        boolean accepted,
        boolean duplicate,
        String workflowInstanceId) {
}
