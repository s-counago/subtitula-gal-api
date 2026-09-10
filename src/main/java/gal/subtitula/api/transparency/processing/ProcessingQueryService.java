package gal.subtitula.api.transparency.processing;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.processing.dto.ProcessingJobResponse;
import gal.subtitula.api.transparency.processing.dto.ProcessingStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Service
public class ProcessingQueryService {

    private final ProjectService projects;
    private final ProcessingJobRepository jobs;

    public ProcessingQueryService(ProjectService projects, ProcessingJobRepository jobs) {
        this.projects = projects;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public ProcessingStatusResponse get(UUID projectId, UUID userId) {
        Project project = projects.get(projectId, userId);
        // A reindex of the still-public version must not hide a correction's
        // active or failed transcription/guide job, including its retry action.
        List<ProcessingJobType> primaryTypes = switch (project.getStatus()) {
            case TRANSCRIBING -> List.of(ProcessingJobType.INGEST);
            case ENRICHING -> List.of(ProcessingJobType.ENRICH);
            case PROCESSING_FAILED -> List.of(ProcessingJobType.INGEST, ProcessingJobType.ENRICH);
            default -> List.of();
        };
        var selected = primaryTypes.isEmpty()
            ? jobs.findFirstByProjectIdOrderByCreatedAtDesc(projectId)
            : jobs.findFirstByProjectIdAndTypeInOrderByCreatedAtDesc(projectId, primaryTypes)
                .or(() -> jobs.findFirstByProjectIdOrderByCreatedAtDesc(projectId));
        ProcessingJobResponse job = selected
            .map(ProcessingQueryService::toResponse)
            .orElse(null);
        return new ProcessingStatusResponse(
            projectId,
            lower(project.getStatus()),
            job);
    }

    private static ProcessingJobResponse toResponse(ProcessingJob job) {
        var error = job.getSafeErrorCode();
        return new ProcessingJobResponse(
            job.getId(),
            lower(job.getType()),
            lower(job.getState()),
            lower(job.getCurrentStage()),
            job.getAttemptCount(),
            job.getUpdatedAt(),
            error == null ? null : lower(error),
            job.getSafeErrorMessage(),
            error != null && error.isRetryable(),
            job.getCostMicrounits(),
            job.getCostCurrency(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getVersion());
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
