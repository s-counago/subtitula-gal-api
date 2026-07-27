package gal.subtitula.api.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.Word;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(UUID id, String name, String language, double durationSec,
                              double speedFactor, String workflowMode, List<Word> words, JsonNode style,
                              JsonNode baseBox, JsonNode segments, Instant approvedAt,
                              Instant createdAt, Instant updatedAt) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getName(), p.getLanguage(), p.getDurationSec(),
            p.getSpeedFactor(), p.getWorkflowMode(), p.getWords(), p.getStyle(),
            p.getBaseBox(), p.getSegments(), p.getApprovedAt(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
