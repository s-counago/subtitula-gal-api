package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectRepository;
import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.TransparencyStateConflictException;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.guide.SessionGuide;
import gal.subtitula.api.transparency.guide.SessionGuideRepository;
import gal.subtitula.api.transparency.lifecycle.InstitutionalProjectStatus;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import gal.subtitula.api.transparency.model.DocumentVisibility;
import gal.subtitula.api.transparency.model.GuideState;
import gal.subtitula.api.transparency.model.ProcessingEventStatus;
import gal.subtitula.api.transparency.model.PublicationState;
import gal.subtitula.api.transparency.model.RecordingUploadState;
import gal.subtitula.api.transparency.model.ReviewIssueSeverity;
import gal.subtitula.api.transparency.model.ReviewIssueState;
import gal.subtitula.api.transparency.model.TranscriptRevisionState;
import gal.subtitula.api.transparency.processing.ProcessingEvent;
import gal.subtitula.api.transparency.processing.ProcessingEventRepository;
import gal.subtitula.api.transparency.processing.ProcessingJob;
import gal.subtitula.api.transparency.processing.ProcessingJobRepository;
import gal.subtitula.api.transparency.publication.dto.CreateCorrectionResponse;
import gal.subtitula.api.transparency.publication.dto.CreatePublicationRequest;
import gal.subtitula.api.transparency.publication.dto.PublicationChecklistItem;
import gal.subtitula.api.transparency.publication.dto.PublicationChecklistResponse;
import gal.subtitula.api.transparency.publication.dto.PublicationResponse;
import gal.subtitula.api.transparency.publication.dto.ReindexPublicationResponse;
import gal.subtitula.api.transparency.publication.dto.StartCorrectionRequest;
import gal.subtitula.api.transparency.publication.dto.WithdrawPublicationRequest;
import gal.subtitula.api.transparency.recording.Recording;
import gal.subtitula.api.transparency.recording.RecordingRepository;
import gal.subtitula.api.transparency.review.ReviewIssueRepository;
import gal.subtitula.api.transparency.search.PublicationReadyForIndexEvent;
import gal.subtitula.api.transparency.search.SearchIndexService;
import gal.subtitula.api.transparency.transcript.EvidenceSegmentRepository;
import gal.subtitula.api.transparency.transcript.TranscriptRevision;
import gal.subtitula.api.transparency.transcript.TranscriptRevisionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PublicationService {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final Pattern VALID_SLUG =
        Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final RecordingRepository recordings;
    private final TranscriptRevisionRepository revisions;
    private final EvidenceSegmentRepository segments;
    private final ReviewIssueRepository reviewIssues;
    private final SessionGuideRepository guides;
    private final ProjectDocumentRepository documents;
    private final PublicationRepository publications;
    private final PublicationDocumentRepository publicationDocuments;
    private final ProcessingJobRepository processingJobs;
    private final ProcessingEventRepository processingEvents;
    private final ApplicationEventPublisher eventPublisher;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransparencyCapabilities capabilities;

    public PublicationService(
            ProjectService projectService,
            ProjectRepository projects,
            RecordingRepository recordings,
            TranscriptRevisionRepository revisions,
            EvidenceSegmentRepository segments,
            ReviewIssueRepository reviewIssues,
            SessionGuideRepository guides,
            ProjectDocumentRepository documents,
            PublicationRepository publications,
            PublicationDocumentRepository publicationDocuments,
            ProcessingJobRepository processingJobs,
            ProcessingEventRepository processingEvents,
            ApplicationEventPublisher eventPublisher,
            NamedParameterJdbcTemplate jdbc,
            TransparencyCapabilities capabilities) {
        this.projectService = projectService;
        this.projects = projects;
        this.recordings = recordings;
        this.revisions = revisions;
        this.segments = segments;
        this.reviewIssues = reviewIssues;
        this.guides = guides;
        this.documents = documents;
        this.publications = publications;
        this.publicationDocuments = publicationDocuments;
        this.processingJobs = processingJobs;
        this.processingEvents = processingEvents;
        this.eventPublisher = eventPublisher;
        this.jdbc = jdbc;
        this.capabilities = capabilities;
    }

    @Transactional(readOnly = true)
    public PublicationChecklistResponse checklist(UUID projectId, UUID userId) {
        Project project = projectService.get(projectId, userId);
        TranscriptRevision revision = latestRevision(projectId);
        Recording recording = defaultRecording(projectId);
        SessionGuide guide = matchingGuide(projectId, revision);
        long requiredIssues = revision == null
            ? 0
            : reviewIssues.countByProjectIdAndStateAndSeverity(
                projectId,
                ReviewIssueState.OPEN,
                ReviewIssueSeverity.REQUIRED);
        List<PublicationChecklistItem> items = new ArrayList<>();
        items.add(item(
            "metadata",
            "Data e órgano da sesión",
            true,
            project.getSessionDate() != null
                && project.getSessionBody() != null
                && !project.getSessionBody().isBlank(),
            "Identifica publicamente a sesión."));
        items.add(item(
            "recording",
            "Gravación verificada e con permiso",
            true,
            recording != null
                && recording.getUploadState() == RecordingUploadState.VERIFIED
                && recording.hasUsagePermission(),
            "Permite reproducir a fonte orixinal."));
        items.add(item(
            "transcript",
            "Transcrición revisada e conxelada",
            true,
            revision != null
                && revision.getState() == TranscriptRevisionState.FROZEN
                && requiredIssues == 0,
            "É a evidencia textual que quedará fixada."));
        items.add(item(
            "guide",
            "Guía asistida con fontes",
            false,
            guide != null,
            "Mellora a navegación, pero non bloquea unha sesión buscable."));
        items.add(item(
            "documents",
            "Documentos oficiais públicos",
            false,
            documents.findByProjectIdOrderByDocumentDateAscTitleAsc(projectId)
                .stream()
                .anyMatch(PublicationService::publishableDocument),
            "Engade contexto e procedencia oficial."));
        boolean publishable = items.stream()
            .filter(PublicationChecklistItem::required)
            .allMatch(PublicationChecklistItem::satisfied)
            && (project.getStatus() == InstitutionalProjectStatus.READY
                || project.getStatus() == InstitutionalProjectStatus.PROCESSING_FAILED);
        return new PublicationChecklistResponse(
            projectId,
            project.getVersion(),
            project.getStatus().name().toLowerCase(Locale.ROOT),
            publishable,
            items,
            recording == null ? null : recording.getId(),
            revision == null ? null : revision.getId(),
            guide == null ? null : guide.getId());
    }

    @Transactional
    public PublicationResponse publish(
            UUID projectId,
            UUID userId,
            CreatePublicationRequest request) {
        requireCapability();
        Project project = projectService.get(projectId, userId);
        checkVersion(project.getVersion(), request.expectedProjectVersion(), "project");
        PublicationChecklistResponse checklist = checklist(projectId, userId);
        if (!checklist.publishable()) {
            throw conflict("Publication requirements are not complete");
        }
        TranscriptRevision revision = revisions.findById(checklist.transcriptRevisionId())
            .orElseThrow(() -> conflict("Frozen transcript not found"));
        UUID recordingId = request.recordingId() == null
            ? checklist.defaultRecordingId()
            : request.recordingId();
        Recording recording = recordings.findByIdAndProjectId(recordingId, projectId)
            .filter(value -> value.getUploadState() == RecordingUploadState.VERIFIED)
            .filter(Recording::hasUsagePermission)
            .orElseThrow(() -> conflict("Recording cannot be published"));
        SessionGuide guide = matchingGuide(projectId, revision);

        List<UUID> requestedDocumentIds = request.documentIds() == null
            ? List.of()
            : request.documentIds().stream().distinct().toList();
        List<ProjectDocument> pinnedDocuments = requestedDocumentIds.isEmpty()
            ? List.of()
            : documents.findByIdInAndProjectId(requestedDocumentIds, projectId);
        if (pinnedDocuments.size() != requestedDocumentIds.size()
                || pinnedDocuments.stream().anyMatch(value -> !publishableDocument(value))) {
            throw conflict("A selected document cannot be published");
        }

        Publication previous = publications
            .findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .orElse(null);
        String correctionNote = bounded(request.correctionNote(), 1_000);
        if (previous != null && correctionNote == null) {
            throw conflict("A correction publication requires a correction note");
        }
        int versionNumber = previous == null ? 1 : previous.getVersionNumber() + 1;
        String slug = previous == null
            ? slug(project, request.desiredSlug())
            : previous.getPublicSlug();
        if (previous != null && previous.getState() == PublicationState.PUBLISHED) {
            previous.supersede();
            publications.flush();
        }
        if (project.getStatus() == InstitutionalProjectStatus.PROCESSING_FAILED) {
            project.transitionTo(InstitutionalProjectStatus.READY);
        }
        Publication publication = publications.save(Publication.publish(
            project,
            slug,
            versionNumber,
            revision.getId(),
            guide == null ? null : guide.getId(),
            recording.getId(),
            userId,
            correctionNote));
        int ordinal = 0;
        for (ProjectDocument document : pinnedDocuments) {
            publicationDocuments.save(PublicationDocument.create(
                publication.getId(),
                document.getId(),
                ordinal++));
        }
        recording.publish();
        if (guide != null && guide.getState() == GuideState.READY) {
            guide.markPublished();
        }
        project.transitionTo(InstitutionalProjectStatus.PUBLISHED);
        publications.flush();
        publicationDocuments.flush();
        recordings.flush();
        guides.flush();
        projects.flush();
        UUID indexJobId = scheduleLexicalIndex(publication);
        return PublicationResponse.from(publication, indexJobId);
    }

    @Transactional(readOnly = true)
    public PublicationResponse latest(UUID projectId, UUID userId) {
        projectService.get(projectId, userId);
        return publications.findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .map(PublicationResponse::from)
            .orElse(null);
    }

    @Transactional
    public ReindexPublicationResponse reindex(
            UUID projectId,
            UUID publicationId,
            UUID userId) {
        projectService.get(projectId, userId);
        if (!capabilities.lexicalSearch() && !capabilities.hybridSearch()) {
            throw conflict("Public search is not enabled");
        }
        Publication publication = publications.findByIdAndProjectId(
                publicationId,
                projectId)
            .filter(value -> value.getState() == PublicationState.PUBLISHED)
            .orElseThrow(() -> conflict("Only an active publication can be reindexed"));
        String idempotencyKey = "reindex:" + publication.getId()
            + ":" + SearchIndexService.INDEX_VERSION
            + ":" + UUID.randomUUID();
        ProcessingJob job = processingJobs.save(ProcessingJob.queued(
            publication.getProjectId(),
            ProcessingJobType.REINDEX,
            idempotencyKey,
            ProcessingStage.QUEUED));
        processingEvents.save(ProcessingEvent.create(
            job.getId(),
            ProcessingStage.QUEUED,
            ProcessingEventStatus.STARTED,
            null,
            null,
            "publication-reindex:" + publication.getId()));
        processingJobs.flush();
        processingEvents.flush();
        eventPublisher.publishEvent(new PublicationReadyForIndexEvent(
            publication.getId(),
            job.getId()));
        return new ReindexPublicationResponse(
            projectId,
            publicationId,
            job.getId(),
            capabilities.hybridSearch());
    }

    @Transactional
    public PublicationResponse withdraw(
            UUID projectId,
            UUID publicationId,
            UUID userId,
            WithdrawPublicationRequest request) {
        Project project = projectService.get(projectId, userId);
        Publication publication = publications
            .findByIdAndProjectId(publicationId, projectId)
            .orElseThrow(() -> conflict("Publication not found"));
        checkVersion(publication.getVersion(), request.expectedVersion(), "publication");
        publication.withdraw();
        jdbc.update("""
            update search_documents
            set active = false, tombstoned_at = now()
            where publication_id = :publicationId and active = true
            """, java.util.Map.of("publicationId", publication.getId()));
        if (project.getStatus() == InstitutionalProjectStatus.PUBLISHED) {
            project.transitionTo(InstitutionalProjectStatus.ARCHIVED);
        }
        publications.flush();
        projects.flush();
        return PublicationResponse.from(publication);
    }

    @Transactional
    public CreateCorrectionResponse startCorrection(
            UUID projectId,
            UUID userId,
            StartCorrectionRequest request) {
        Project project = projectService.get(projectId, userId);
        checkVersion(project.getVersion(), request.expectedProjectVersion(), "project");
        if (project.getStatus() != InstitutionalProjectStatus.PUBLISHED) {
            throw conflict("Only a published project can start a correction");
        }
        Publication active = publications
            .findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .filter(value -> value.getState() == PublicationState.PUBLISHED)
            .orElseThrow(() -> conflict("Active publication not found"));
        TranscriptRevision parent = revisions.findById(active.getTranscriptRevisionId())
            .orElseThrow(() -> conflict("Published transcript not found"));
        int versionNumber = revisions
            .findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .map(value -> value.getVersionNumber() + 1)
            .orElse(1);
        TranscriptRevision correction = revisions.save(
            TranscriptRevision.createCorrection(
                projectId,
                versionNumber,
                parent.getId(),
                parent.getLanguageCode(),
                parent.getContentHash(),
                userId));
        int sequence = 0;
        for (var segment : segments
                .findByTranscriptRevisionIdOrderBySequenceAsc(parent.getId())) {
            segments.save(segment.copyToRevision(correction.getId(), sequence++));
        }
        project.transitionTo(InstitutionalProjectStatus.REVIEW_REQUIRED);
        revisions.flush();
        segments.flush();
        projects.flush();
        return new CreateCorrectionResponse(
            projectId,
            correction.getId(),
            correction.getVersionNumber(),
            project.getStatus().name().toLowerCase(Locale.ROOT),
            project.getVersion());
    }

    private TranscriptRevision latestRevision(UUID projectId) {
        return revisions.findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .orElse(null);
    }

    private Recording defaultRecording(UUID projectId) {
        return recordings.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .filter(value -> value.getUploadState() == RecordingUploadState.VERIFIED)
            .filter(Recording::hasUsagePermission)
            .findFirst()
            .orElse(null);
    }

    private SessionGuide matchingGuide(
            UUID projectId,
            TranscriptRevision revision) {
        if (revision == null) {
            return null;
        }
        return guides.findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .filter(value -> value.getTranscriptRevisionId().equals(revision.getId()))
            .filter(value -> value.getState() == GuideState.READY
                || value.getState() == GuideState.PUBLISHED)
            .orElse(null);
    }

    private void requireCapability() {
        if (!capabilities.publicPublication()) {
            throw conflict("Public publication is not enabled");
        }
    }

    private UUID scheduleLexicalIndex(Publication publication) {
        if (!capabilities.lexicalSearch() && !capabilities.hybridSearch()) {
            return null;
        }
        String idempotencyKey = "index:" + publication.getId()
            + ":" + SearchIndexService.INDEX_VERSION;
        ProcessingJob job = processingJobs.findByIdempotencyKey(idempotencyKey)
            .orElseGet(() -> processingJobs.save(ProcessingJob.queued(
                publication.getProjectId(),
                ProcessingJobType.INDEX,
                idempotencyKey,
                ProcessingStage.QUEUED)));
        processingEvents.save(ProcessingEvent.create(
            job.getId(),
            ProcessingStage.QUEUED,
            ProcessingEventStatus.STARTED,
            null,
            null,
            "publication:" + publication.getId()));
        processingJobs.flush();
        processingEvents.flush();
        eventPublisher.publishEvent(new PublicationReadyForIndexEvent(
            publication.getId(),
            job.getId()));
        return job.getId();
    }

    private static boolean publishableDocument(ProjectDocument value) {
        return value.hasPublicationPermission()
            && value.getVisibility() == DocumentVisibility.PUBLIC
            && value.getOfficialUrl() != null;
    }

    private static PublicationChecklistItem item(
            String key,
            String label,
            boolean required,
            boolean satisfied,
            String detail) {
        return new PublicationChecklistItem(key, label, required, satisfied, detail);
    }

    private static String slug(Project project, String requested) {
        if (requested != null && !requested.isBlank()) {
            String value = requested.trim().toLowerCase(Locale.ROOT);
            if (value.length() > 160 || !VALID_SLUG.matcher(value).matches()) {
                throw conflict("Publication slug is invalid");
            }
            return value;
        }
        String base = Normalizer.normalize(project.getName(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
        base = NON_SLUG.matcher(base).replaceAll("-")
            .replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "sesion";
        }
        if (base.length() > 145) {
            base = base.substring(0, 145).replaceAll("-+$", "");
        }
        return base + "-" + project.getId().toString().substring(0, 8);
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > max) {
            throw conflict("Publication value is too long");
        }
        return normalized;
    }

    private static void checkVersion(long actual, long expected, String resource) {
        if (actual != expected) {
            throw conflict("Stale " + resource);
        }
    }

    private static TransparencyStateConflictException conflict(String message) {
        return new TransparencyStateConflictException(message);
    }
}
