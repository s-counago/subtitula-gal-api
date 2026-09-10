package gal.subtitula.api.transparency.processing;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.lifecycle.InstitutionalProjectStatus;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProcessingQueryServiceTest {
    @Test
    void publishedVersionMaintenanceDoesNotHideCorrectionEnrichment() {
        UUID projectId = UUID.randomUUID(), userId = UUID.randomUUID();
        ProjectService projects = mock(ProjectService.class);
        ProcessingJobRepository jobs = mock(ProcessingJobRepository.class);
        Project project = mock(Project.class);
        when(projects.get(projectId, userId)).thenReturn(project);
        ProcessingJob guide = ProcessingJob.queued(projectId, ProcessingJobType.ENRICH,
            "correction-guide", ProcessingStage.QUEUED);
        ProcessingQueryService service = new ProcessingQueryService(projects, jobs);

        when(project.getStatus()).thenReturn(InstitutionalProjectStatus.ENRICHING);
        when(jobs.findFirstByProjectIdAndTypeInOrderByCreatedAtDesc(
            projectId, List.of(ProcessingJobType.ENRICH))).thenReturn(Optional.of(guide));
        assertThat(service.get(projectId, userId).job().type()).isEqualTo("enrich");

        when(project.getStatus()).thenReturn(InstitutionalProjectStatus.PROCESSING_FAILED);
        when(jobs.findFirstByProjectIdAndTypeInOrderByCreatedAtDesc(projectId,
            List.of(ProcessingJobType.INGEST, ProcessingJobType.ENRICH)))
            .thenReturn(Optional.of(guide));
        assertThat(service.get(projectId, userId).job().type()).isEqualTo("enrich");
        verify(jobs, never()).findFirstByProjectIdOrderByCreatedAtDesc(projectId);

        when(project.getStatus()).thenReturn(InstitutionalProjectStatus.PUBLISHED);
        ProcessingJob index = ProcessingJob.queued(projectId, ProcessingJobType.REINDEX,
            "public-index", ProcessingStage.QUEUED);
        when(jobs.findFirstByProjectIdOrderByCreatedAtDesc(projectId))
            .thenReturn(Optional.of(index));
        assertThat(service.get(projectId, userId).job().type()).isEqualTo("reindex");
    }
}
