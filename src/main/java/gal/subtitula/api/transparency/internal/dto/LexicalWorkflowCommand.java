package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record LexicalWorkflowCommand(
        UUID projectId,
        String workflowInstanceId) {
}
