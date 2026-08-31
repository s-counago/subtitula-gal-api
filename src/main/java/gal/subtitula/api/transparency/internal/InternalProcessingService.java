package gal.subtitula.api.transparency.internal;

import com.fasterxml.jackson.databind.JsonNode;
import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectRepository;
import gal.subtitula.api.transparency.agenda.AgendaAlignment;
import gal.subtitula.api.transparency.agenda.AgendaAlignmentRepository;
import gal.subtitula.api.transparency.agenda.AgendaItem;
import gal.subtitula.api.transparency.agenda.AgendaItemRepository;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.guide.GuideContribution;
import gal.subtitula.api.transparency.guide.GuideContributionRepository;
import gal.subtitula.api.transparency.guide.GuideDecision;
import gal.subtitula.api.transparency.guide.GuideDecisionRepository;
import gal.subtitula.api.transparency.guide.GuideEvidenceLink;
import gal.subtitula.api.transparency.guide.GuideEvidenceLinkRepository;
import gal.subtitula.api.transparency.guide.GuideTopic;
import gal.subtitula.api.transparency.guide.GuideTopicRepository;
import gal.subtitula.api.transparency.guide.SessionGuide;
import gal.subtitula.api.transparency.guide.SessionGuideRepository;
import gal.subtitula.api.transparency.ingest.ProviderWebhookDelivery;
import gal.subtitula.api.transparency.ingest.ProviderWebhookDeliveryRepository;
import gal.subtitula.api.transparency.ingest.UploadIntent;
import gal.subtitula.api.transparency.ingest.UploadIntentRepository;
import gal.subtitula.api.transparency.internal.dto.CompleteUploadCommand;
import gal.subtitula.api.transparency.internal.dto.CreateUploadIntentCommand;
import gal.subtitula.api.transparency.internal.dto.FailJobCommand;
import gal.subtitula.api.transparency.internal.dto.ExpiredUploadCandidateResponse;
import gal.subtitula.api.transparency.internal.dto.ExpiredUploadCandidatesCommand;
import gal.subtitula.api.transparency.internal.dto.FinalizeExpiredUploadCommand;
import gal.subtitula.api.transparency.internal.dto.IngestTranscriptCommand;
import gal.subtitula.api.transparency.internal.dto.IngestTranscriptResponse;
import gal.subtitula.api.transparency.internal.dto.EnrichmentContextResponse;
import gal.subtitula.api.transparency.internal.dto.EnrichmentStartResponse;
import gal.subtitula.api.transparency.internal.dto.IngestGuideCommand;
import gal.subtitula.api.transparency.internal.dto.IngestGuideResponse;
import gal.subtitula.api.transparency.internal.dto.IngestAgendaCommand;
import gal.subtitula.api.transparency.internal.dto.IngestAgendaResponse;
import gal.subtitula.api.transparency.internal.dto.JobContextResponse;
import gal.subtitula.api.transparency.internal.dto.ProviderSubmittedCommand;
import gal.subtitula.api.transparency.internal.dto.RecordingDeletionCandidateResponse;
import gal.subtitula.api.transparency.internal.dto.RecordingMediaContextResponse;
import gal.subtitula.api.transparency.internal.dto.ConfirmRecordingDeletionCommand;
import gal.subtitula.api.transparency.internal.dto.PendingRecordingDeletionsCommand;
import gal.subtitula.api.transparency.internal.dto.PendingWorkflowResponse;
import gal.subtitula.api.transparency.internal.dto.PendingWorkflowsCommand;
import gal.subtitula.api.transparency.internal.dto.RetryIngestCommand;
import gal.subtitula.api.transparency.internal.dto.StartJobCommand;
import gal.subtitula.api.transparency.internal.dto.StartEnrichmentCommand;
import gal.subtitula.api.transparency.internal.dto.UploadIntentInternalResponse;
import gal.subtitula.api.transparency.internal.dto.WebhookReceivedCommand;
import gal.subtitula.api.transparency.internal.dto.WebhookReceivedResponse;
import gal.subtitula.api.transparency.lifecycle.InstitutionalProjectStatus;
import gal.subtitula.api.transparency.lifecycle.ProcessingErrorCode;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobState;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import gal.subtitula.api.transparency.model.ProcessingEventStatus;
import gal.subtitula.api.transparency.model.AgendaAlignmentState;
import gal.subtitula.api.transparency.model.ContributionKind;
import gal.subtitula.api.transparency.model.GuideEvidenceSubjectType;
import gal.subtitula.api.transparency.model.ReviewIssueSeverity;
import gal.subtitula.api.transparency.model.ReviewIssueType;
import gal.subtitula.api.transparency.model.RecordingUploadState;
import gal.subtitula.api.transparency.model.UploadIntentState;
import gal.subtitula.api.transparency.processing.ProcessingEvent;
import gal.subtitula.api.transparency.processing.ProcessingEventRepository;
import gal.subtitula.api.transparency.processing.ProcessingJob;
import gal.subtitula.api.transparency.processing.ProcessingJobRepository;
import gal.subtitula.api.transparency.recording.Recording;
import gal.subtitula.api.transparency.recording.RecordingRepository;
import gal.subtitula.api.transparency.review.ReviewIssue;
import gal.subtitula.api.transparency.review.ReviewIssueRepository;
import gal.subtitula.api.transparency.transcript.EvidenceSegment;
import gal.subtitula.api.transparency.transcript.EvidenceSegmentRepository;
import gal.subtitula.api.transparency.transcript.Speaker;
import gal.subtitula.api.transparency.transcript.SpeakerRepository;
import gal.subtitula.api.transparency.transcript.TranscriptRevision;
import gal.subtitula.api.transparency.transcript.TranscriptRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class InternalProcessingService {

    private static final long MAX_INITIAL_UPLOAD_BYTES = 2_000_000_000L;
    private static final Pattern SAFE_ENVIRONMENT = Pattern.compile("^[a-z0-9-]{1,30}$");
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");

    private final ProjectRepository projects;
    private final RecordingRepository recordings;
    private final UploadIntentRepository intents;
    private final ProcessingJobRepository jobs;
    private final ProcessingEventRepository events;
    private final ProviderWebhookDeliveryRepository deliveries;
    private final TranscriptRevisionRepository revisions;
    private final SpeakerRepository speakers;
    private final EvidenceSegmentRepository segments;
    private final ReviewIssueRepository issues;
    private final AgendaItemRepository agendaItems;
    private final AgendaAlignmentRepository agendaAlignments;
    private final SessionGuideRepository guides;
    private final GuideTopicRepository guideTopics;
    private final GuideContributionRepository guideContributions;
    private final GuideDecisionRepository guideDecisions;
    private final GuideEvidenceLinkRepository guideEvidenceLinks;
    private final TransparencyCapabilities capabilities;

    public InternalProcessingService(
            ProjectRepository projects,
            RecordingRepository recordings,
            UploadIntentRepository intents,
            ProcessingJobRepository jobs,
            ProcessingEventRepository events,
            ProviderWebhookDeliveryRepository deliveries,
            TranscriptRevisionRepository revisions,
            SpeakerRepository speakers,
            EvidenceSegmentRepository segments,
            ReviewIssueRepository issues,
            AgendaItemRepository agendaItems,
            AgendaAlignmentRepository agendaAlignments,
            SessionGuideRepository guides,
            GuideTopicRepository guideTopics,
            GuideContributionRepository guideContributions,
            GuideDecisionRepository guideDecisions,
            GuideEvidenceLinkRepository guideEvidenceLinks,
            TransparencyCapabilities capabilities) {
        this.projects = projects;
        this.recordings = recordings;
        this.intents = intents;
        this.jobs = jobs;
        this.events = events;
        this.deliveries = deliveries;
        this.revisions = revisions;
        this.speakers = speakers;
        this.segments = segments;
        this.issues = issues;
        this.agendaItems = agendaItems;
        this.agendaAlignments = agendaAlignments;
        this.guides = guides;
        this.guideTopics = guideTopics;
        this.guideContributions = guideContributions;
        this.guideDecisions = guideDecisions;
        this.guideEvidenceLinks = guideEvidenceLinks;
        this.capabilities = capabilities;
    }

    @Transactional
    public UploadIntentInternalResponse createUploadIntent(CreateUploadIntentCommand command) {
        UploadIntent existing = intents.findByProjectIdAndClientRequestId(
            command.projectId(),
            command.clientRequestId()).orElse(null);
        if (existing != null) {
            return response(existing);
        }
        validateUploadCommand(command);
        Project project = project(command.projectId());
        if (!"institution".equals(project.getWorkflowMode())) {
            throw conflict("Durable ingestion is only available for institutional projects");
        }
        if (project.getStatus() != InstitutionalProjectStatus.DRAFT
                && project.getStatus() != InstitutionalProjectStatus.PROCESSING_FAILED) {
            throw conflict("Project is not ready for an upload intent");
        }

        String organizationPart = project.getOrganizationId() == null
            ? "personal"
            : project.getOrganizationId().toString();
        String key = command.environment()
            + "/organizations/" + organizationPart
            + "/projects/" + project.getId()
            + "/recordings/" + command.recordingId()
            + "/original";
        Recording recording = Recording.createUploadIntent(
            command.recordingId(),
            project.getId(),
            key,
            bounded(command.originalFilename(), 500),
            command.mimeType(),
            command.sizeBytes(),
            normalizedHash(command.checksumSha256()),
            command.usagePermission());
        ProcessingJob job = ProcessingJob.queued(
            command.jobId(),
            project.getId(),
            ProcessingJobType.INGEST,
            "ingest:" + project.getId() + ":" + command.clientRequestId(),
            ProcessingStage.QUEUED);
        UploadIntent intent = UploadIntent.create(
            command.intentId(),
            project.getId(),
            recording.getId(),
            job.getId(),
            command.clientRequestId(),
            command.expiresAt());

        project.transitionTo(InstitutionalProjectStatus.UPLOADING);
        recordings.save(recording);
        jobs.save(job);
        intents.save(intent);
        projects.save(project);
        events.save(ProcessingEvent.create(
            job.getId(),
            ProcessingStage.QUEUED,
            ProcessingEventStatus.STARTED,
            null,
            null,
            "upload-intent:" + intent.getId()));
        flushAggregate();
        return response(intent);
    }

    @Transactional
    public UploadIntentInternalResponse completeUpload(
            UUID intentId,
            CompleteUploadCommand command) {
        UploadIntent intent = intent(intentId);
        if (intent.getState().name().equals("COMPLETED")) {
            return response(intent);
        }
        checkVersion(intent.getVersion(), command.expectedIntentVersion(), "upload intent");
        if (intent.isExpired(Instant.now())) {
            intent.expire();
            throw conflict("Upload intent expired");
        }
        Recording recording = recording(intent);
        if (recording.getSizeBytes() != command.sizeBytes()) {
            throw conflict("Uploaded object size does not match the intent");
        }
        if (!recording.getMimeType().equalsIgnoreCase(command.mimeType())) {
            throw conflict("Uploaded object type does not match the intent");
        }
        String expectedHash = recording.getChecksumSha256();
        if (expectedHash != null && !expectedHash.equals(normalizedHash(command.checksumSha256()))) {
            throw conflict("Uploaded object checksum does not match the intent");
        }
        Project project = project(intent.getProjectId());
        ProcessingJob job = job(intent.getProcessingJobId());
        recording.verify(bounded(command.etag(), 255));
        intent.complete();
        job.assignWorkflow(command.workflowInstanceId());
        project.transitionTo(InstitutionalProjectStatus.UPLOADED);
        recordings.save(recording);
        jobs.save(job);
        intents.save(intent);
        projects.save(project);
        events.save(ProcessingEvent.create(
            job.getId(),
            ProcessingStage.VALIDATING_UPLOAD,
            ProcessingEventStatus.SUCCEEDED,
            null,
            null,
            command.workflowInstanceId()));
        flushAggregate();
        return response(intent);
    }

    @Transactional
    public RecordingDeletionCandidateResponse abortUpload(UUID intentId) {
        UploadIntent intent = intent(intentId);
        if (intent.getState().name().equals("ABORTED")) {
            Recording existing = recording(intent);
            return deletionCandidate(existing);
        }
        Recording recording = recording(intent);
        ProcessingJob job = job(intent.getProcessingJobId());
        Project project = project(intent.getProjectId());
        intent.abort();
        recording.abort();
        job.fail(ProcessingErrorCode.UPLOAD_MISSING, "A subida foi cancelada.");
        project.fail("upload_cancelled", "A subida foi cancelada.");
        flushAggregate();
        return deletionCandidate(recording);
    }

    @Transactional(readOnly = true)
    public List<ExpiredUploadCandidateResponse> expiredUploadCandidates(
            ExpiredUploadCandidatesCommand command) {
        Instant now = Instant.now();
        Instant cutoff = command == null || command.expiredBefore() == null
            ? now
            : command.expiredBefore().isAfter(now)
                ? now
                : command.expiredBefore();
        int limit = command == null || command.limit() == null
            ? 100
            : Math.max(1, Math.min(command.limit(), 100));
        return intents
            .findByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                UploadIntentState.CREATED,
                cutoff,
                PageRequest.of(0, limit))
            .stream()
            .map(intent -> {
                Recording recording = recording(intent);
                return new ExpiredUploadCandidateResponse(
                    intent.getId(),
                    intent.getProjectId(),
                    recording.getId(),
                    recording.getObjectKey(),
                    intent.getExpiresAt(),
                    intent.getVersion(),
                    recording.getVersion());
            })
            .toList();
    }

    @Transactional
    public RecordingDeletionCandidateResponse finalizeExpiredUpload(
            UUID intentId,
            FinalizeExpiredUploadCommand command) {
        UploadIntent intent = intent(intentId);
        if (intent.getState() != UploadIntentState.CREATED) {
            return null;
        }
        if (!intent.isExpired(Instant.now())) {
            throw conflict("Upload intent has not expired");
        }
        Recording recording = recording(intent);
        checkVersion(
            intent.getVersion(),
            command.expectedIntentVersion(),
            "upload intent");
        checkVersion(
            recording.getVersion(),
            command.expectedRecordingVersion(),
            "recording");
        ProcessingJob job = job(intent.getProcessingJobId());
        Project project = project(intent.getProjectId());

        intent.expire();
        recording.expire();
        job.fail(
            ProcessingErrorCode.UPLOAD_INTENT_EXPIRED,
            "A autorización da subida caducou.");
        project.fail(
            "upload_intent_expired",
            "A autorización da subida caducou.");
        events.save(ProcessingEvent.create(
            job.getId(),
            ProcessingStage.VALIDATING_UPLOAD,
            ProcessingEventStatus.FAILED,
            null,
            null,
            "expired-upload-cleanup:" + intent.getId()));
        flushAggregate();
        return deletionCandidate(recording);
    }

    @Transactional(readOnly = true)
    public List<RecordingDeletionCandidateResponse> pendingRecordingDeletions(
            PendingRecordingDeletionsCommand command) {
        int limit = command == null || command.limit() == null
            ? 100
            : Math.max(1, Math.min(command.limit(), 100));
        return recordings.findByUploadStateInOrderByCreatedAtAsc(
                List.of(RecordingUploadState.ABORTED, RecordingUploadState.EXPIRED),
            PageRequest.of(0, limit))
            .stream()
            .map(InternalProcessingService::deletionCandidate)
            .toList();
    }

    @Transactional
    public void confirmRecordingDeletion(
            UUID recordingId,
            ConfirmRecordingDeletionCommand command) {
        Recording recording = recordings.findById(recordingId)
            .orElseThrow(() -> conflict("Recording not found"));
        if (recording.getUploadState() == RecordingUploadState.DELETED) {
            return;
        }
        checkVersion(
            recording.getVersion(),
            command.expectedRecordingVersion(),
            "recording");
        recording.confirmObjectDeleted();
        recordings.flush();
    }

    @Transactional(readOnly = true)
    public RecordingMediaContextResponse privateMedia(UUID projectId) {
        project(projectId);
        Recording recording = recordings.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .filter(value -> value.getUploadState() == RecordingUploadState.VERIFIED)
            .findFirst()
            .orElseThrow(() -> conflict("Verified recording not found"));
        return new RecordingMediaContextResponse(
            recording.getObjectKey(),
            recording.getMimeType(),
            recording.getSizeBytes(),
            recording.getEtag());
    }

    @Transactional(readOnly = true)
    public List<PendingWorkflowResponse> pendingWorkflows(
            PendingWorkflowsCommand command) {
        int limit = command == null || command.limit() == null
            ? 100
            : Math.max(1, Math.min(command.limit(), 100));
        return jobs.findByStateInOrderByCreatedAtAsc(
                List.of(
                    ProcessingJobState.QUEUED,
                    ProcessingJobState.RUNNING,
                    ProcessingJobState.FAILED_RETRYABLE),
                PageRequest.of(0, limit))
            .stream()
            .filter(this::needsWorkflow)
            .map(job -> {
                UUID publicationId = publicationId(job);
                String workflowId = job.getWorkflowInstanceId();
                if (workflowId == null
                        || job.getState() == ProcessingJobState.FAILED_RETRYABLE) {
                    workflowId = switch (job.getType()) {
                        case ENRICH -> "enrich-" + job.getId();
                        case INDEX, REINDEX -> "index-" + publicationId + "-"
                            + job.getId() + "-" + (job.getAttemptCount() + 1);
                        default -> "ingest-" + job.getId();
                    };
                }
                return new PendingWorkflowResponse(
                    job.getId(),
                    job.getProjectId(),
                    lower(job.getType()),
                    workflowId,
                    publicationId);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public JobContextResponse jobContext(UUID jobId) {
        ProcessingJob job = job(jobId);
        if (job.getType() != ProcessingJobType.INGEST) {
            throw conflict("Job is not an ingestion job");
        }
        UploadIntent intent = intents.findByProcessingJobId(jobId)
            .orElseThrow(() -> conflict("Upload intent not found for job"));
        Recording recording = recording(intent);
        Project project = project(job.getProjectId());
        return new JobContextResponse(
            job.getId(),
            project.getId(),
            recording.getId(),
            recording.getObjectKey(),
            recording.getMimeType(),
            recording.getSizeBytes(),
            recording.getChecksumSha256(),
            project.getLanguage(),
            lower(job.getState()),
            lower(job.getCurrentStage()),
            job.getVersion(),
            project.getVersion());
    }

    @Transactional(readOnly = true)
    public EnrichmentContextResponse enrichmentContext(UUID jobId) {
        ProcessingJob job = job(jobId);
        if (job.getType() != ProcessingJobType.ENRICH) {
            throw conflict("Job is not an enrichment job");
        }
        Project project = project(job.getProjectId());
        if (project.getStatus() != InstitutionalProjectStatus.ENRICHING
                && project.getStatus() != InstitutionalProjectStatus.READY
                && project.getStatus() != InstitutionalProjectStatus.PROCESSING_FAILED) {
            throw conflict("Project is not ready for enrichment");
        }
        TranscriptRevision revision = revisions
            .findFirstByProjectIdOrderByVersionNumberDesc(project.getId())
            .orElseThrow(() -> conflict("Transcript revision not found"));
        if (!revision.getState().name().equals("FROZEN")) {
            throw conflict("Enrichment requires a frozen transcript revision");
        }
        Map<UUID, Speaker> bySpeaker = speakers
            .findByProjectIdOrderByProviderLabelAsc(project.getId())
            .stream()
            .collect(Collectors.toMap(Speaker::getId, Function.identity()));
        List<EnrichmentContextResponse.Segment> segmentResponses = segments
            .findByTranscriptRevisionIdOrderBySequenceAsc(revision.getId())
            .stream()
            .map(value -> {
                Speaker speaker = bySpeaker.get(value.getSpeakerId());
                return new EnrichmentContextResponse.Segment(
                    value.getId(),
                    value.getSequence(),
                    value.getStartMs(),
                    value.getEndMs(),
                    value.getSpeakerId(),
                    speaker == null
                        ? "Persoa non identificada"
                        : speaker.getDisplayLabel(),
                    value.getReviewedText());
            })
            .toList();
        if (segmentResponses.isEmpty()) {
            throw conflict("Frozen transcript has no evidence segments");
        }
        List<EnrichmentContextResponse.AgendaItem> agendaResponses = agendaItems
            .findByProjectIdOrderByOrdinalAsc(project.getId())
            .stream()
            .map(value -> new EnrichmentContextResponse.AgendaItem(
                value.getId(),
                value.getOrdinal(),
                value.getExternalIdentifier(),
                value.getTitle(),
                value.getDescription()))
            .toList();
        return new EnrichmentContextResponse(
            job.getId(),
            project.getId(),
            revision.getId(),
            revision.getContentHash(),
            revision.getLanguageCode(),
            project.getName(),
            project.getSessionDate(),
            project.getSessionBody(),
            capabilities.automaticAgenda(),
            capabilities.structuredGuide(),
            job.getVersion(),
            project.getVersion(),
            segmentResponses,
            agendaResponses);
    }

    @Transactional
    public EnrichmentStartResponse startEnrichment(
            UUID jobId,
            StartEnrichmentCommand command) {
        ProcessingJob job = job(jobId);
        Project project = project(job.getProjectId());
        if (job.getType() != ProcessingJobType.ENRICH) {
            throw conflict("Job is not an enrichment job");
        }
        if (job.getWorkflowInstanceId() != null
                && job.getWorkflowInstanceId().equals(command.workflowInstanceId())
                && job.getState() == ProcessingJobState.RUNNING) {
            return enrichmentStartResponse(job, project);
        }
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        checkVersion(project.getVersion(), command.expectedProjectVersion(), "project");
        if (project.getStatus() != InstitutionalProjectStatus.ENRICHING
                && project.getStatus() != InstitutionalProjectStatus.PROCESSING_FAILED) {
            throw conflict("Project is not waiting for enrichment");
        }
        String workflowInstanceId = boundedRequired(command.workflowInstanceId(), 255);
        if (job.getWorkflowInstanceId() != null
                && !job.getWorkflowInstanceId().equals(workflowInstanceId)
                && job.getState() != ProcessingJobState.FAILED_RETRYABLE) {
            throw conflict("Enrichment job already belongs to another Workflow");
        }
        if (project.getStatus() == InstitutionalProjectStatus.PROCESSING_FAILED) {
            project.transitionTo(InstitutionalProjectStatus.ENRICHING);
        }
        job.assignWorkflow(workflowInstanceId);
        job.start(ProcessingStage.ALIGNING_AGENDA);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.ALIGNING_AGENDA,
            ProcessingEventStatus.STARTED,
            null,
            null,
            job.getWorkflowInstanceId()));
        jobs.flush();
        projects.flush();
        events.flush();
        return enrichmentStartResponse(job, project);
    }

    @Transactional
    public JobContextResponse startJob(UUID jobId, StartJobCommand command) {
        ProcessingJob job = job(jobId);
        Project project = project(job.getProjectId());
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        checkVersion(project.getVersion(), command.expectedProjectVersion(), "project");
        if (job.getState() != ProcessingJobState.RUNNING) {
            job.start(ProcessingStage.SUBMITTING_PROVIDER);
        }
        if (project.getStatus() != InstitutionalProjectStatus.TRANSCRIBING) {
            project.transitionTo(InstitutionalProjectStatus.TRANSCRIBING);
        }
        UploadIntent intent = intents.findByProcessingJobId(jobId)
            .orElseThrow(() -> conflict("Upload intent not found for job"));
        Recording recording = recording(intent);
        recording.markProviderSourceActive();
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.SUBMITTING_PROVIDER,
            ProcessingEventStatus.STARTED,
            null,
            null,
            job.getWorkflowInstanceId()));
        flushAggregate();
        return jobContext(jobId);
    }

    @Transactional
    public JobContextResponse retryIngest(UUID jobId, RetryIngestCommand command) {
        ProcessingJob job = job(jobId);
        Project project = project(job.getProjectId());
        if (job.getType() != ProcessingJobType.INGEST
                || job.getState() != ProcessingJobState.FAILED_RETRYABLE
                || project.getStatus() != InstitutionalProjectStatus.PROCESSING_FAILED) {
            throw conflict("Ingestion job is not retryable");
        }
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        checkVersion(project.getVersion(), command.expectedProjectVersion(), "project");
        UploadIntent intent = intents.findByProcessingJobId(jobId)
            .orElseThrow(() -> conflict("Upload intent not found for job"));
        Recording recording = recording(intent);
        if (intent.getState() != UploadIntentState.COMPLETED
                || recording.getUploadState() != RecordingUploadState.VERIFIED) {
            throw conflict("Verified upload is not available for retry");
        }
        String workflow = boundedRequired(command.workflowInstanceId(), 255);
        job.retry(workflow, ProcessingStage.SUBMITTING_PROVIDER);
        project.transitionTo(InstitutionalProjectStatus.TRANSCRIBING);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.SUBMITTING_PROVIDER,
            ProcessingEventStatus.STARTED,
            null,
            null,
            workflow));
        flushAggregate();
        return jobContext(jobId);
    }

    @Transactional
    public JobContextResponse providerSubmitted(UUID jobId, ProviderSubmittedCommand command) {
        ProcessingJob job = job(jobId);
        String requestId = boundedRequired(command.providerRequestId(), 255);
        if (requestId.equals(job.getProviderRequestId())) {
            return jobContext(jobId);
        }
        if (job.getProviderRequestId() != null) {
            throw conflict("Job already has another provider request");
        }
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        job.waitForProvider(requestId);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.WAITING_FOR_PROVIDER,
            ProcessingEventStatus.WAITING,
            null,
            null,
            job.getWorkflowInstanceId()));
        flushAggregate();
        return jobContext(jobId);
    }

    @Transactional
    public WebhookReceivedResponse webhookReceived(
            UUID jobId,
            WebhookReceivedCommand command) {
        validateHash(command.payloadDigest());
        ProcessingJob job = job(jobId);
        if (!job.getProjectId().equals(project(job.getProjectId()).getId())) {
            throw conflict("Job project is invalid");
        }
        String requestId = boundedRequired(command.providerRequestId(), 255);
        if (job.getProviderRequestId() == null) {
            if (job.getState() == ProcessingJobState.RUNNING) {
                job.waitForProvider(requestId);
            } else {
                throw conflict("Provider request has not been submitted");
            }
        } else if (!job.getProviderRequestId().equals(requestId)) {
            throw conflict("Provider request does not belong to this job");
        }
        ProviderWebhookDelivery existing = deliveries
            .findByProviderRequestId(requestId)
            .orElse(null);
        boolean duplicate = existing != null;
        if (duplicate) {
            if (!existing.getProcessingJobId().equals(jobId)
                    || !existing.getPayloadDigest().equals(command.payloadDigest())
                    || !existing.getArtifactKey().equals(command.artifactKey())) {
                throw conflict("Provider request was already delivered with different content");
            }
        } else {
            deliveries.save(ProviderWebhookDelivery.create(
                jobId,
                requestId,
                command.payloadDigest(),
                boundedRequired(command.artifactKey(), 1024)));
            events.save(ProcessingEvent.create(
                jobId,
                ProcessingStage.STORING_PROVIDER_ARTIFACT,
                ProcessingEventStatus.SUCCEEDED,
                null,
                null,
                job.getWorkflowInstanceId()));
        }
        String workflowId = job.getWorkflowInstanceId();
        if (workflowId == null || !workflowId.equals(command.workflowInstanceId())) {
            throw conflict("Webhook Workflow correlation is invalid");
        }
        return new WebhookReceivedResponse(!duplicate, duplicate, workflowId);
    }

    @Transactional
    public IngestTranscriptResponse ingestTranscript(
            UUID jobId,
            IngestTranscriptCommand command) {
        validateHash(command.contentHash());
        ProcessingJob job = job(jobId);
        Project project = project(job.getProjectId());
        TranscriptRevision duplicate = revisions
            .findByProjectIdAndContentHash(project.getId(), command.contentHash())
            .orElse(null);
        if (duplicate != null) {
            recordUsage(
                job,
                command.providerUsage(),
                command.costMicrounits(),
                command.costCurrency());
            if (job.getState() != ProcessingJobState.SUCCEEDED) {
                job.succeed(command.contentHash(), command.rawArtifactKey());
            }
            if (project.getStatus() == InstitutionalProjectStatus.TRANSCRIBING) {
                project.transitionTo(InstitutionalProjectStatus.REVIEW_REQUIRED);
            }
            return new IngestTranscriptResponse(
                duplicate.getId(),
                segments.findByTranscriptRevisionIdOrderBySequenceAsc(duplicate.getId()).size(),
                0,
                true);
        }
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        checkVersion(project.getVersion(), command.expectedProjectVersion(), "project");
        if (command.segments() == null || command.segments().isEmpty()) {
            throw conflict("Normalized transcript has no evidence segments");
        }

        int nextVersion = revisions.findFirstByProjectIdOrderByVersionNumberDesc(project.getId())
            .map(value -> value.getVersionNumber() + 1)
            .orElse(1);
        TranscriptRevision revision = revisions.save(TranscriptRevision.createAsr(
            project.getId(),
            nextVersion,
            boundedRequired(command.provider(), 80),
            boundedRequired(command.model(), 120),
            boundedRequired(command.languageCode(), 20),
            bounded(command.keytermVersion(), 80),
            boundedRequired(command.rawArtifactKey(), 1024),
            command.contentHash()));

        Map<String, Speaker> byProviderLabel = new LinkedHashMap<>();
        if (command.speakers() != null) {
            command.speakers().forEach(value -> {
                String label = normalizedSpeakerLabel(value.providerLabel());
                byProviderLabel.computeIfAbsent(
                    label,
                    ignored -> speakers.save(Speaker.createUnknown(project.getId(), label)));
            });
        }
        int expectedSequence = 0;
        int requiredIssues = 0;
        long previousStart = -1;
        for (var value : command.segments()) {
            if (value.sequence() != expectedSequence++) {
                throw conflict("Evidence segment sequence is not contiguous");
            }
            if (value.startMs() < 0 || value.endMs() <= value.startMs()
                    || value.startMs() < previousStart
                    || value.text() == null || value.text().isBlank()) {
                throw conflict("Evidence segment is invalid");
            }
            previousStart = value.startMs();
            String label = normalizedSpeakerLabel(value.speakerProviderLabel());
            Speaker speaker = byProviderLabel.computeIfAbsent(
                label,
                ignored -> speakers.save(Speaker.createUnknown(project.getId(), label)));
            EvidenceSegment segment = segments.save(EvidenceSegment.create(
                revision.getId(),
                value.sequence(),
                value.startMs(),
                value.endMs(),
                speaker.getId(),
                value.text().trim(),
                value.wordTimings(),
                value.signals()));
            var signals = value.signals();
            if (signals != null && signals.path("hasLowLogProbability").asBoolean(false)) {
                requiredIssues += addIssue(
                    project,
                    revision,
                    segment,
                    speaker,
                    ReviewIssueType.LOW_CONFIDENCE_SPAN,
                    ReviewIssueSeverity.REQUIRED,
                    signals);
            }
            if (signals != null && signals.path("hasMissingTiming").asBoolean(false)) {
                requiredIssues += addIssue(
                    project,
                    revision,
                    segment,
                    speaker,
                    ReviewIssueType.MISSING_TIMING,
                    ReviewIssueSeverity.REQUIRED,
                    signals);
            }
            if (signals != null && signals.path("speakerChanged").asBoolean(false)) {
                addIssue(
                    project,
                    revision,
                    segment,
                    speaker,
                    ReviewIssueType.SPEAKER_CHANGE,
                    ReviewIssueSeverity.WARNING,
                    signals);
            }
            if (signals != null && signals.path("hasAudioEvent").asBoolean(false)) {
                addIssue(
                    project,
                    revision,
                    segment,
                    speaker,
                    ReviewIssueType.OVERLAP_OR_NOISE,
                    ReviewIssueSeverity.WARNING,
                    signals);
            }
            if (signals != null
                    && signals.path("properNameCandidates").isArray()
                    && !signals.path("properNameCandidates").isEmpty()) {
                addIssue(
                    project,
                    revision,
                    segment,
                    speaker,
                    ReviewIssueType.PROBABLE_PROPER_NAME,
                    ReviewIssueSeverity.WARNING,
                    signals);
            }
        }

        for (Speaker speaker : byProviderLabel.values()) {
            issues.save(ReviewIssue.open(
                project.getId(),
                revision.getId(),
                null,
                speaker.getId(),
                ReviewIssueType.UNKNOWN_SPEAKER,
                ReviewIssueSeverity.REQUIRED,
                null));
            requiredIssues++;
        }
        project.setLanguage(command.languageCode());
        project.setDurationSec(command.durationMs() / 1000.0);
        project.transitionTo(InstitutionalProjectStatus.REVIEW_REQUIRED);
        job.advance(ProcessingStage.PERSISTING_TRANSCRIPT);
        recordUsage(
            job,
            command.providerUsage(),
            command.costMicrounits(),
            command.costCurrency());
        job.succeed(command.contentHash(), command.rawArtifactKey());
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.PERSISTING_TRANSCRIPT,
            ProcessingEventStatus.SUCCEEDED,
            null,
            command.providerUsage(),
            job.getWorkflowInstanceId()));
        return new IngestTranscriptResponse(
            revision.getId(),
            command.segments().size(),
            requiredIssues,
            false);
    }

    @Transactional
    public IngestAgendaResponse ingestAgenda(
            UUID jobId,
            IngestAgendaCommand command) {
        validateHash(command.contentHash());
        ProcessingJob job = job(jobId);
        Project project = project(job.getProjectId());
        if (job.getType() != ProcessingJobType.ENRICH) {
            throw conflict("Job is not an enrichment job");
        }
        TranscriptRevision revision = revisions.findById(command.transcriptRevisionId())
            .filter(value -> value.getProjectId().equals(project.getId()))
            .orElseThrow(() -> conflict("Transcript revision does not belong to the project"));
        if (job.getState() == ProcessingJobState.SUCCEEDED
                && command.contentHash().equals(job.getOutputHash())) {
            List<AgendaAlignment> existing =
                agendaAlignments.findByTranscriptRevisionIdOrderByOccurrenceAsc(revision.getId());
            return new IngestAgendaResponse(
                existing.size(),
                (int) existing.stream().filter(AgendaAlignment::isRequiresHumanCheck).count(),
                true);
        }
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        checkVersion(project.getVersion(), command.expectedProjectVersion(), "project");
        if (job.getState() != ProcessingJobState.RUNNING
                || project.getStatus() != InstitutionalProjectStatus.ENRICHING) {
            throw conflict("Enrichment job is not running");
        }
        if (!revision.getState().name().equals("FROZEN")) {
            throw conflict("Agenda evidence must use a frozen transcript revision");
        }
        Map<UUID, EvidenceSegment> bySegment = segments
            .findByTranscriptRevisionIdOrderBySequenceAsc(revision.getId())
            .stream()
            .collect(Collectors.toMap(EvidenceSegment::getId, Function.identity()));
        Map<UUID, AgendaItem> byAgenda = agendaItems
            .findByProjectIdOrderByOrdinalAsc(project.getId())
            .stream()
            .collect(Collectors.toMap(AgendaItem::getId, Function.identity()));
        List<IngestGuideCommand.Alignment> requested =
            command.alignments() == null ? List.of() : command.alignments();
        validateAlignments(requested, byAgenda, bySegment);

        agendaAlignments.deleteByTranscriptRevisionId(revision.getId());
        for (IngestGuideCommand.Alignment value : requested) {
            agendaAlignments.save(AgendaAlignment.create(
                revision.getId(),
                value.agendaItemId(),
                value.occurrence(),
                value.startSegmentId(),
                value.endSegmentId(),
                value.signals(),
                enumValue(
                    AgendaAlignmentState.class,
                    value.state(),
                    AgendaAlignmentState.AUTOMATIC),
                value.requiresHumanCheck(),
                strictRequired(value.algorithmVersion(), 120, "alignment algorithm"),
                value.revisited()));
        }
        job.configure(
            revision.getContentHash(),
            revision.getRawArtifactKey(),
            "deterministic-agenda-alignment-v1",
            null,
            null);
        job.succeed(command.contentHash(), command.rawArtifactKey());
        project.transitionTo(InstitutionalProjectStatus.READY);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.ALIGNING_AGENDA,
            ProcessingEventStatus.SUCCEEDED,
            null,
            null,
            job.getWorkflowInstanceId()));
        agendaAlignments.flush();
        jobs.flush();
        projects.flush();
        events.flush();
        return new IngestAgendaResponse(
            requested.size(),
            (int) requested.stream()
                .filter(IngestGuideCommand.Alignment::requiresHumanCheck)
                .count(),
            false);
    }

    @Transactional
    public IngestGuideResponse ingestGuide(
            UUID jobId,
            IngestGuideCommand command) {
        validateHash(command.contentHash());
        ProcessingJob job = job(jobId);
        Project project = project(job.getProjectId());
        if (job.getType() != ProcessingJobType.ENRICH) {
            throw conflict("Job is not an enrichment job");
        }
        SessionGuide duplicate = guides
            .findByProjectIdAndGeneratorContentHash(project.getId(), command.contentHash())
            .orElse(null);
        if (duplicate != null) {
            recordUsage(
                job,
                command.providerUsage(),
                command.costMicrounits(),
                command.costCurrency());
            if (job.getState() != ProcessingJobState.SUCCEEDED) {
                job.succeed(command.contentHash(), command.rawArtifactKey());
            }
            if (project.getStatus() == InstitutionalProjectStatus.ENRICHING) {
                project.transitionTo(InstitutionalProjectStatus.READY);
            }
            return guideResponse(duplicate, true);
        }
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        checkVersion(project.getVersion(), command.expectedProjectVersion(), "project");
        if (job.getState() != ProcessingJobState.RUNNING
                || project.getStatus() != InstitutionalProjectStatus.ENRICHING) {
            throw conflict("Enrichment job is not running");
        }
        TranscriptRevision revision = revisions.findById(command.transcriptRevisionId())
            .filter(value -> value.getProjectId().equals(project.getId()))
            .orElseThrow(() -> conflict("Transcript revision does not belong to the project"));
        if (!revision.getState().name().equals("FROZEN")) {
            throw conflict("Guide evidence must use a frozen transcript revision");
        }

        List<EvidenceSegment> revisionSegments =
            segments.findByTranscriptRevisionIdOrderBySequenceAsc(revision.getId());
        Map<UUID, EvidenceSegment> bySegment = revisionSegments.stream()
            .collect(Collectors.toMap(EvidenceSegment::getId, Function.identity()));
        Map<UUID, AgendaItem> byAgenda = agendaItems
            .findByProjectIdOrderByOrdinalAsc(project.getId())
            .stream()
            .collect(Collectors.toMap(AgendaItem::getId, Function.identity()));
        Set<UUID> projectSpeakers = speakers
            .findByProjectIdOrderByProviderLabelAsc(project.getId())
            .stream()
            .map(Speaker::getId)
            .collect(Collectors.toSet());

        List<IngestGuideCommand.Alignment> requestedAlignments =
            command.alignments() == null ? List.of() : command.alignments();
        validateAlignments(requestedAlignments, byAgenda, bySegment);
        List<IngestGuideCommand.Topic> requestedTopics =
            command.topics() == null ? List.of() : command.topics();
        validateGuide(requestedTopics, byAgenda, bySegment, projectSpeakers);

        job.advance(ProcessingStage.ALIGNING_AGENDA);
        agendaAlignments.deleteByTranscriptRevisionId(revision.getId());
        for (IngestGuideCommand.Alignment value : requestedAlignments) {
            agendaAlignments.save(AgendaAlignment.create(
                revision.getId(),
                value.agendaItemId(),
                value.occurrence(),
                value.startSegmentId(),
                value.endSegmentId(),
                value.signals(),
                enumValue(
                    AgendaAlignmentState.class,
                    value.state(),
                    AgendaAlignmentState.AUTOMATIC),
                value.requiresHumanCheck(),
                strictRequired(value.algorithmVersion(), 120, "alignment algorithm"),
                value.revisited()));
        }
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.ALIGNING_AGENDA,
            ProcessingEventStatus.SUCCEEDED,
            null,
            null,
            job.getWorkflowInstanceId()));

        job.advance(ProcessingStage.GENERATING_GUIDE);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.GENERATING_GUIDE,
            ProcessingEventStatus.SUCCEEDED,
            null,
            null,
            job.getWorkflowInstanceId()));
        job.advance(ProcessingStage.VALIDATING_GUIDE);

        int versionNumber = guides
            .findFirstByProjectIdOrderByVersionNumberDesc(project.getId())
            .map(value -> value.getVersionNumber() + 1)
            .orElse(1);
        SessionGuide guide = guides.save(SessionGuide.create(
            project.getId(),
            revision.getId(),
            versionNumber,
            strictRequired(command.schemaVersion(), 40, "guide schema"),
            strictRequired(command.generationModel(), 160, "generation model"),
            strictRequired(command.promptVersion(), 120, "prompt version"),
            command.contentHash(),
            strictRequired(command.rawArtifactKey(), 1024, "guide artifact"),
            false));

        int contributionCount = 0;
        int decisionCount = 0;
        for (IngestGuideCommand.Topic value : requestedTopics) {
            GuideTopic topic = guideTopics.save(GuideTopic.create(
                value.id(),
                guide.getId(),
                value.ordinal(),
                strictRequired(value.title(), 500, "topic title"),
                strictRequired(value.neutralSummary(), 2_000, "topic summary"),
                value.aliases(),
                value.agendaItemId(),
                value.startSegmentId(),
                value.endSegmentId()));
            persistEvidence(
                guide.getId(),
                GuideEvidenceSubjectType.TOPIC,
                topic.getId(),
                value.evidenceSegmentIds());

            List<IngestGuideCommand.Contribution> topicContributions =
                value.contributions() == null ? List.of() : value.contributions();
            for (IngestGuideCommand.Contribution contribution : topicContributions) {
                ContributionKind kind = enumValue(
                    ContributionKind.class,
                    contribution.kind(),
                    ContributionKind.OTHER);
                if ((kind == ContributionKind.SUPPORT || kind == ContributionKind.OBJECTION)
                        && !contribution.explicitClassification()) {
                    kind = ContributionKind.OTHER;
                }
                GuideContribution saved = guideContributions.save(GuideContribution.create(
                    contribution.id(),
                    topic.getId(),
                    contribution.ordinal(),
                    contribution.speakerId(),
                    kind,
                    strictRequired(
                        contribution.neutralSummary(),
                        2_000,
                        "contribution summary")));
                persistEvidence(
                    guide.getId(),
                    GuideEvidenceSubjectType.CONTRIBUTION,
                    saved.getId(),
                    contribution.evidenceSegmentIds());
                contributionCount++;
            }

            List<IngestGuideCommand.Decision> topicDecisions =
                value.decisions() == null ? List.of() : value.decisions();
            for (IngestGuideCommand.Decision decision : topicDecisions) {
                GuideDecision saved = guideDecisions.save(GuideDecision.candidate(
                    decision.id(),
                    topic.getId(),
                    decision.agendaItemId(),
                    decision.ordinal(),
                    strictRequired(
                        decision.neutralDescription(),
                        2_000,
                        "decision description"),
                    strictOptional(decision.motion(), 4_000, "decision motion"),
                    strictOptional(decision.result(), 500, "decision result"),
                    decision.voteDetails()));
                persistEvidence(
                    guide.getId(),
                    GuideEvidenceSubjectType.DECISION,
                    saved.getId(),
                    decision.evidenceSegmentIds());
                decisionCount++;
            }
        }
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.VALIDATING_GUIDE,
            ProcessingEventStatus.SUCCEEDED,
            null,
            null,
            job.getWorkflowInstanceId()));

        job.advance(ProcessingStage.PERSISTING_GUIDE);
        job.configure(
            revision.getContentHash(),
            revision.getRawArtifactKey(),
            command.generationModel(),
            command.promptVersion(),
            null);
        recordUsage(
            job,
            command.providerUsage(),
            command.costMicrounits(),
            command.costCurrency());
        job.succeed(command.contentHash(), command.rawArtifactKey());
        project.transitionTo(InstitutionalProjectStatus.READY);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.PERSISTING_GUIDE,
            ProcessingEventStatus.SUCCEEDED,
            null,
            command.providerUsage(),
            job.getWorkflowInstanceId()));
        guideEvidenceLinks.flush();
        guideDecisions.flush();
        guideContributions.flush();
        guideTopics.flush();
        guides.flush();
        agendaAlignments.flush();
        jobs.flush();
        projects.flush();
        events.flush();
        return new IngestGuideResponse(
            guide.getId(),
            requestedTopics.size(),
            contributionCount,
            decisionCount,
            (int) requestedAlignments.stream()
                .filter(IngestGuideCommand.Alignment::requiresHumanCheck)
                .count(),
            false);
    }

    @Transactional
    public void failJob(UUID jobId, FailJobCommand command) {
        ProcessingJob job = job(jobId);
        Project project = project(job.getProjectId());
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        checkVersion(project.getVersion(), command.expectedProjectVersion(), "project");
        ProcessingErrorCode code;
        try {
            code = ProcessingErrorCode.valueOf(command.errorCode().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            code = ProcessingErrorCode.UNKNOWN;
        }
        String message = bounded(command.safeMessage(), 500);
        job.fail(code, message);
        project.fail(lower(code), message);
        events.save(ProcessingEvent.create(
            jobId,
            job.getCurrentStage(),
            ProcessingEventStatus.FAILED,
            null,
            null,
            job.getWorkflowInstanceId()));
    }

    private void validateAlignments(
            List<IngestGuideCommand.Alignment> values,
            Map<UUID, AgendaItem> byAgenda,
            Map<UUID, EvidenceSegment> bySegment) {
        if (values.size() > Math.max(400, byAgenda.size() * 3)) {
            throw conflict("Too many agenda alignment occurrences");
        }
        Set<String> occurrences = new HashSet<>();
        List<IngestGuideCommand.Alignment> chronological = new ArrayList<>(values);
        chronological.sort(Comparator.comparingInt(value ->
            requiredSegment(bySegment, value.startSegmentId()).getSequence()));
        int lastMainOrdinal = -1;
        for (IngestGuideCommand.Alignment value : chronological) {
            AgendaItem agendaItem = byAgenda.get(value.agendaItemId());
            if (agendaItem == null) {
                throw conflict("Agenda alignment references another project");
            }
            if (value.occurrence() < 0
                    || !occurrences.add(value.agendaItemId() + ":" + value.occurrence())) {
                throw conflict("Agenda alignment occurrence is invalid");
            }
            EvidenceSegment start = requiredSegment(bySegment, value.startSegmentId());
            EvidenceSegment end = requiredSegment(bySegment, value.endSegmentId());
            if (start.getSequence() > end.getSequence()) {
                throw conflict("Agenda alignment range is reversed");
            }
            if (!value.revisited() && value.occurrence() == 0) {
                if (agendaItem.getOrdinal() < lastMainOrdinal) {
                    throw conflict("Agenda alignment is not monotonic");
                }
                lastMainOrdinal = agendaItem.getOrdinal();
            }
            AgendaAlignmentState state = enumValue(
                AgendaAlignmentState.class,
                value.state(),
                AgendaAlignmentState.AUTOMATIC);
            if (state == AgendaAlignmentState.UNRESOLVED
                    && !value.requiresHumanCheck()) {
                throw conflict("Unresolved agenda alignment must expose an optional check");
            }
            strictRequired(value.algorithmVersion(), 120, "alignment algorithm");
        }
    }

    private void validateGuide(
            List<IngestGuideCommand.Topic> values,
            Map<UUID, AgendaItem> byAgenda,
            Map<UUID, EvidenceSegment> bySegment,
            Set<UUID> projectSpeakers) {
        if (values.isEmpty() || values.size() > 100) {
            throw conflict("Guide must contain between one and one hundred topics");
        }
        Set<UUID> topicIds = new HashSet<>();
        Set<UUID> contributionIds = new HashSet<>();
        Set<UUID> decisionIds = new HashSet<>();
        for (int topicIndex = 0; topicIndex < values.size(); topicIndex++) {
            IngestGuideCommand.Topic topic = values.get(topicIndex);
            if (topic.id() == null || !topicIds.add(topic.id())
                    || topic.ordinal() != topicIndex) {
                throw conflict("Guide topic identity or order is invalid");
            }
            strictRequired(topic.title(), 500, "topic title");
            strictRequired(topic.neutralSummary(), 2_000, "topic summary");
            validateAliases(topic.aliases());
            if (topic.agendaItemId() != null
                    && !byAgenda.containsKey(topic.agendaItemId())) {
                throw conflict("Guide topic references another agenda");
            }
            EvidenceSegment start = requiredSegment(bySegment, topic.startSegmentId());
            EvidenceSegment end = requiredSegment(bySegment, topic.endSegmentId());
            if (start.getSequence() > end.getSequence()) {
                throw conflict("Guide topic range is reversed");
            }
            validateEvidence(
                topic.evidenceSegmentIds(),
                bySegment,
                start.getSequence(),
                end.getSequence());

            List<IngestGuideCommand.Contribution> topicContributions =
                topic.contributions() == null ? List.of() : topic.contributions();
            if (topicContributions.size() > 100) {
                throw conflict("Guide topic has too many contributions");
            }
            for (int contributionIndex = 0;
                    contributionIndex < topicContributions.size();
                    contributionIndex++) {
                IngestGuideCommand.Contribution contribution =
                    topicContributions.get(contributionIndex);
                if (contribution.id() == null
                        || !contributionIds.add(contribution.id())
                        || contribution.ordinal() != contributionIndex) {
                    throw conflict("Guide contribution identity or order is invalid");
                }
                if (contribution.speakerId() != null
                        && !projectSpeakers.contains(contribution.speakerId())) {
                    throw conflict("Guide contribution references another speaker");
                }
                strictRequired(
                    contribution.neutralSummary(),
                    2_000,
                    "contribution summary");
                enumValue(
                    ContributionKind.class,
                    contribution.kind(),
                    ContributionKind.OTHER);
                validateEvidence(
                    contribution.evidenceSegmentIds(),
                    bySegment,
                    start.getSequence(),
                    end.getSequence());
            }

            List<IngestGuideCommand.Decision> topicDecisions =
                topic.decisions() == null ? List.of() : topic.decisions();
            if (topicDecisions.size() > 30) {
                throw conflict("Guide topic has too many decision candidates");
            }
            for (int decisionIndex = 0;
                    decisionIndex < topicDecisions.size();
                    decisionIndex++) {
                IngestGuideCommand.Decision decision = topicDecisions.get(decisionIndex);
                if (decision.id() == null
                        || !decisionIds.add(decision.id())
                        || decision.ordinal() != decisionIndex) {
                    throw conflict("Guide decision identity or order is invalid");
                }
                if (decision.agendaItemId() != null
                        && !byAgenda.containsKey(decision.agendaItemId())) {
                    throw conflict("Guide decision references another agenda");
                }
                strictRequired(
                    decision.neutralDescription(),
                    2_000,
                    "decision description");
                strictOptional(decision.motion(), 4_000, "decision motion");
                strictOptional(decision.result(), 500, "decision result");
                validateEvidence(
                    decision.evidenceSegmentIds(),
                    bySegment,
                    start.getSequence(),
                    end.getSequence());
            }
        }
    }

    private void persistEvidence(
            UUID guideId,
            GuideEvidenceSubjectType type,
            UUID subjectId,
            List<UUID> evidenceSegmentIds) {
        int ordinal = 0;
        for (UUID segmentId : evidenceSegmentIds) {
            guideEvidenceLinks.save(GuideEvidenceLink.create(
                guideId,
                type,
                subjectId,
                segmentId,
                "SUPPORT",
                ordinal++));
        }
    }

    private static void validateEvidence(
            List<UUID> evidenceSegmentIds,
            Map<UUID, EvidenceSegment> bySegment,
            int rangeStart,
            int rangeEnd) {
        if (evidenceSegmentIds == null
                || evidenceSegmentIds.isEmpty()
                || evidenceSegmentIds.size() > 8) {
            throw conflict("Every guide object requires one to eight evidence segments");
        }
        Set<UUID> unique = new HashSet<>();
        for (UUID segmentId : evidenceSegmentIds) {
            EvidenceSegment segment = requiredSegment(bySegment, segmentId);
            if (!unique.add(segmentId)
                    || segment.getSequence() < rangeStart
                    || segment.getSequence() > rangeEnd) {
                throw conflict("Guide evidence is duplicated or outside its topic");
            }
        }
    }

    private static void validateAliases(com.fasterxml.jackson.databind.JsonNode aliases) {
        if (aliases == null || aliases.isNull()) {
            return;
        }
        if (!aliases.isArray() || aliases.size() > 20) {
            throw conflict("Topic aliases are invalid");
        }
        aliases.forEach(value -> {
            if (!value.isTextual()
                    || value.asText().isBlank()
                    || value.asText().length() > 120) {
                throw conflict("Topic alias is invalid");
            }
        });
    }

    private IngestGuideResponse guideResponse(SessionGuide guide, boolean duplicate) {
        List<GuideTopic> topics = guideTopics.findByGuideIdOrderByOrdinalAsc(guide.getId());
        List<UUID> topicIds = topics.stream().map(GuideTopic::getId).toList();
        int contributionCount = topicIds.isEmpty()
            ? 0
            : guideContributions.findByTopicIdInOrderByOrdinalAsc(topicIds).size();
        int decisionCount = topicIds.isEmpty()
            ? 0
            : guideDecisions.findByTopicIdInOrderByOrdinalAsc(topicIds).size();
        return new IngestGuideResponse(
            guide.getId(),
            topics.size(),
            contributionCount,
            decisionCount,
            (int) agendaAlignments
                .countByTranscriptRevisionIdAndRequiresHumanCheckTrue(
                    guide.getTranscriptRevisionId()),
            duplicate);
    }

    private static EnrichmentStartResponse enrichmentStartResponse(
            ProcessingJob job,
            Project project) {
        return new EnrichmentStartResponse(
            job.getId(),
            project.getId(),
            lower(job.getState()),
            lower(job.getCurrentStage()),
            job.getVersion(),
            project.getVersion());
    }

    private static EvidenceSegment requiredSegment(
            Map<UUID, EvidenceSegment> bySegment,
            UUID segmentId) {
        EvidenceSegment segment = bySegment.get(segmentId);
        if (segment == null) {
            throw conflict("Evidence segment does not belong to the frozen transcript");
        }
        return segment;
    }

    private static String strictRequired(String value, int max, String label) {
        String normalized = strictOptional(value, max, label);
        if (normalized == null) {
            throw conflict("Required " + label + " is missing");
        }
        return normalized;
    }

    private static String strictOptional(String value, int max, String label) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw conflict(label + " is too long");
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type,
            String value,
            T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw conflict("Unsupported generated enum value");
        }
    }

    private static RecordingDeletionCandidateResponse deletionCandidate(
            Recording recording) {
        return new RecordingDeletionCandidateResponse(
            recording.getId(),
            recording.getObjectKey(),
            recording.getVersion());
    }

    private boolean needsWorkflow(ProcessingJob job) {
        return switch (job.getType()) {
            case INGEST -> capabilities.durableInstitutionalUpload()
                && (job.getState() == ProcessingJobState.QUEUED
                    || job.getState() == ProcessingJobState.RUNNING);
            case ENRICH -> (capabilities.automaticAgenda() || capabilities.structuredGuide())
                && (job.getState() == ProcessingJobState.QUEUED
                    || job.getState() == ProcessingJobState.RUNNING);
            case INDEX, REINDEX -> (
                (capabilities.lexicalSearch()
                    && (job.getState() == ProcessingJobState.QUEUED
                        || job.getState() == ProcessingJobState.FAILED_RETRYABLE))
                || (capabilities.lexicalSearch()
                    && capabilities.hybridSearch()
                    && job.getState() == ProcessingJobState.RUNNING
                    && job.getCurrentStage() == ProcessingStage.GENERATING_EMBEDDINGS))
                && publicationId(job) != null;
            default -> false;
        };
    }

    private static UUID publicationId(ProcessingJob job) {
        if (job.getType() != ProcessingJobType.INDEX
                && job.getType() != ProcessingJobType.REINDEX) {
            return null;
        }
        String[] parts = job.getIdempotencyKey().split(":", 3);
        if (parts.length < 2) {
            return null;
        }
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UploadIntentInternalResponse response(UploadIntent intent) {
        Recording recording = recording(intent);
        ProcessingJob job = job(intent.getProcessingJobId());
        Project project = project(intent.getProjectId());
        return new UploadIntentInternalResponse(
            intent.getId(),
            project.getId(),
            recording.getId(),
            job.getId(),
            recording.getObjectKey(),
            recording.getMimeType(),
            recording.getSizeBytes(),
            recording.getChecksumSha256(),
            intent.getExpiresAt(),
            lower(intent.getState()),
            intent.getVersion(),
            job.getVersion(),
            project.getVersion());
    }

    private void validateUploadCommand(CreateUploadIntentCommand command) {
        if (command.environment() == null
                || !SAFE_ENVIRONMENT.matcher(command.environment()).matches()) {
            throw conflict("Invalid environment");
        }
        if (command.sizeBytes() <= 0 || command.sizeBytes() >= MAX_INITIAL_UPLOAD_BYTES) {
            throw conflict("Upload is outside the initial provider size limit");
        }
        if (command.mimeType() == null
                || !(command.mimeType().startsWith("video/")
                || command.mimeType().startsWith("audio/"))) {
            throw conflict("Unsupported media type");
        }
        normalizedHash(command.checksumSha256());
        Instant now = Instant.now();
        if (command.expiresAt() == null
                || !command.expiresAt().isAfter(now)
                || command.expiresAt().isAfter(now.plusSeconds(3600))) {
            throw conflict("Invalid upload expiry");
        }
    }

    private static String normalizedSpeakerLabel(String value) {
        if (value == null || value.isBlank()) {
            return "speaker_unknown";
        }
        return boundedRequired(value, 120);
    }

    private static String normalizedHash(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        validateHash(normalized);
        return normalized;
    }

    private static void validateHash(String value) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw conflict("Invalid SHA-256 digest");
        }
    }

    private static void recordUsage(
            ProcessingJob job,
            JsonNode usage,
            Long costMicrounits,
            String costCurrency) {
        if (usage == null && costMicrounits == null && costCurrency == null) {
            return;
        }
        if (usage != null && (!usage.isObject() || usage.toString().length() > 20_000)) {
            throw conflict("Provider usage payload is invalid");
        }
        if (costMicrounits == null
                || costMicrounits < 0
                || costMicrounits > 1_000_000_000_000L
                || costCurrency == null
                || !costCurrency.matches("^[A-Z]{3}$")) {
            throw conflict("Provider cost payload is invalid");
        }
        job.recordUsage(usage, costMicrounits, costCurrency);
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String boundedRequired(String value, int max) {
        if (value == null || value.isBlank()) {
            throw conflict("Required processing value is missing");
        }
        return bounded(value, max);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static void checkVersion(long actual, long expected, String resource) {
        if (actual != expected) {
            throw conflict("Stale " + resource + " version");
        }
    }

    private Project project(UUID id) {
        return projects.findById(id).orElseThrow(() -> conflict("Project not found"));
    }

    private UploadIntent intent(UUID id) {
        return intents.findById(id).orElseThrow(() -> conflict("Upload intent not found"));
    }

    private Recording recording(UploadIntent intent) {
        return recordings.findByIdAndProjectId(intent.getRecordingId(), intent.getProjectId())
            .orElseThrow(() -> conflict("Recording not found"));
    }

    private ProcessingJob job(UUID id) {
        return jobs.findById(id).orElseThrow(() -> conflict("Processing job not found"));
    }

    private void flushAggregate() {
        recordings.flush();
        intents.flush();
        jobs.flush();
        projects.flush();
        events.flush();
    }

    private int addIssue(
            Project project,
            TranscriptRevision revision,
            EvidenceSegment segment,
            Speaker speaker,
            ReviewIssueType type,
            ReviewIssueSeverity severity,
            com.fasterxml.jackson.databind.JsonNode signals) {
        issues.save(ReviewIssue.open(
            project.getId(),
            revision.getId(),
            segment.getId(),
            speaker.getId(),
            type,
            severity,
            signals));
        return severity == ReviewIssueSeverity.REQUIRED ? 1 : 0;
    }

    private static InternalProcessingConflictException conflict(String message) {
        return new InternalProcessingConflictException(message);
    }
}
