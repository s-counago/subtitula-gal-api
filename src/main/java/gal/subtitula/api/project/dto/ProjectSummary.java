package gal.subtitula.api.project.dto;

import gal.subtitula.api.project.Project;
import java.time.Instant;
import java.util.UUID;

public record ProjectSummary(UUID id, String name, String language, double durationSec,
                             double speedFactor, String workflowMode, Instant createdAt) {
    public static ProjectSummary from(Project p) {
        return new ProjectSummary(p.getId(), p.getName(), p.getLanguage(),
            p.getDurationSec(), p.getSpeedFactor(), p.getWorkflowMode(), p.getCreatedAt());
    }
}
