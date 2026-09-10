package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record LexicalIndexResponse(
        UUID projectId,
        boolean semanticIndexRequired,
        String workflowInstanceId) {
    public LexicalIndexResponse(UUID projectId, boolean semanticIndexRequired) {
        this(projectId, semanticIndexRequired, null);
    }
}
