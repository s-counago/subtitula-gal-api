package gal.subtitula.api.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.Word;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(UUID id, String name, String language, double durationSec,
                              double speedFactor, String workflowMode, List<Word> words, JsonNode style,
                              String status, UUID organizationId, LocalDate sessionDate,
                              String body, String location, String sessionType,
                              String failureCode, String failureMessage, long version,
                              Instant createdAt, Instant updatedAt) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getName(), p.getLanguage(), p.getDurationSec(),
            p.getSpeedFactor(), p.getWorkflowMode(), p.getWords(), p.getStyle(),
            p.getStatus().name().toLowerCase(java.util.Locale.ROOT), p.getOrganizationId(),
            p.getSessionDate(), p.getSessionBody(), p.getLocation(), p.getSessionType(),
            p.getFailureCode(), p.getFailureMessage(), p.getVersion(),
            p.getCreatedAt(), p.getUpdatedAt());
    }
}
