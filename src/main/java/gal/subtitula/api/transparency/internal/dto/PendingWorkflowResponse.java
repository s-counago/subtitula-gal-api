package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record PendingWorkflowResponse(
        UUID jobId,
        UUID projectId,
        String type,
        String workflowInstanceId,
        UUID publicationId) {
}
