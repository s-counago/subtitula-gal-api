package gal.subtitula.api.transparency.processing.dto;

import java.util.UUID;

public record ProcessingStatusResponse(
        UUID projectId,
        String projectStatus,
        ProcessingJobResponse job) {
}
