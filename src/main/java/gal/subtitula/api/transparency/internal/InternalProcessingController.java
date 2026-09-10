package gal.subtitula.api.transparency.internal;

import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/internal/processing")
public class InternalProcessingController {

    private final InternalProcessingService service;
    private final TransparencyCapabilities capabilities;

    public InternalProcessingController(
            InternalProcessingService service,
            TransparencyCapabilities capabilities) {
        this.service = service;
        this.capabilities = capabilities;
    }

    @PostMapping("/upload-intents")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadIntentInternalResponse createUploadIntent(
            @RequestBody CreateUploadIntentCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        return service.createUploadIntent(command);
    }

    @PostMapping("/upload-intents/{intentId}/complete")
    public UploadIntentInternalResponse completeUpload(
            @PathVariable UUID intentId,
            @RequestBody CompleteUploadCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        return service.completeUpload(intentId, command);
    }

    @PostMapping("/upload-intents/{intentId}/abort")
    public RecordingDeletionCandidateResponse abortUpload(@PathVariable UUID intentId) {
        capabilities.requireDurableInstitutionalUpload();
        return service.abortUpload(intentId);
    }

    @PostMapping("/cleanup/upload-intents/candidates")
    public List<ExpiredUploadCandidateResponse> expiredUploadCandidates(
            @RequestBody ExpiredUploadCandidatesCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        return service.expiredUploadCandidates(command);
    }

    @PostMapping("/cleanup/upload-intents/{intentId}/complete")
    public ResponseEntity<RecordingDeletionCandidateResponse> finalizeExpiredUpload(
            @PathVariable UUID intentId,
            @RequestBody FinalizeExpiredUploadCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        RecordingDeletionCandidateResponse candidate =
            service.finalizeExpiredUpload(intentId, command);
        return candidate == null
            ? ResponseEntity.noContent().build()
            : ResponseEntity.ok(candidate);
    }

    @PostMapping("/cleanup/recordings/candidates")
    public List<RecordingDeletionCandidateResponse> pendingRecordingDeletions(
            @RequestBody PendingRecordingDeletionsCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        return service.pendingRecordingDeletions(command);
    }

    @PostMapping("/cleanup/recordings/{recordingId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmRecordingDeletion(
            @PathVariable UUID recordingId,
            @RequestBody ConfirmRecordingDeletionCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        service.confirmRecordingDeletion(recordingId, command);
    }

    @GetMapping("/projects/{projectId}/media")
    public RecordingMediaContextResponse privateMedia(@PathVariable UUID projectId) {
        capabilities.requireDurableInstitutionalUpload();
        return service.privateMedia(projectId);
    }

    @PostMapping("/jobs/pending-workflows")
    public List<PendingWorkflowResponse> pendingWorkflows(
            @RequestBody PendingWorkflowsCommand command) {
        return service.pendingWorkflows(command);
    }

    @GetMapping("/jobs/{jobId}/context")
    public JobContextResponse jobContext(@PathVariable UUID jobId) {
        capabilities.requireDurableInstitutionalUpload();
        return service.jobContext(jobId);
    }

    @GetMapping("/jobs/{jobId}/enrichment-context")
    public EnrichmentContextResponse enrichmentContext(@PathVariable UUID jobId) {
        capabilities.requireEnrichment();
        return service.enrichmentContext(jobId);
    }

    @PostMapping("/jobs/{jobId}/enrichment-start")
    public EnrichmentStartResponse startEnrichment(
            @PathVariable UUID jobId,
            @RequestBody StartEnrichmentCommand command) {
        capabilities.requireEnrichment();
        return service.startEnrichment(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/start")
    public JobContextResponse startJob(
            @PathVariable UUID jobId,
            @RequestBody StartJobCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        return service.startJob(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/ingest-retry")
    public JobContextResponse retryIngest(
            @PathVariable UUID jobId,
            @RequestBody RetryIngestCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        return service.retryIngest(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/provider-submitted")
    public JobContextResponse providerSubmitted(
            @PathVariable UUID jobId,
            @RequestBody ProviderSubmittedCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        return service.providerSubmitted(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/webhook-received")
    public WebhookReceivedResponse webhookReceived(
            @PathVariable UUID jobId,
            @RequestBody WebhookReceivedCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        return service.webhookReceived(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/transcript")
    public IngestTranscriptResponse ingestTranscript(
            @PathVariable UUID jobId,
            @RequestBody IngestTranscriptCommand command) {
        capabilities.requireDurableInstitutionalUpload();
        capabilities.requireNormalizedTranscript();
        return service.ingestTranscript(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/guide")
    public IngestGuideResponse ingestGuide(
            @PathVariable UUID jobId,
            @RequestBody IngestGuideCommand command) {
        capabilities.requireStructuredGuide();
        return service.ingestGuide(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/agenda")
    public IngestAgendaResponse ingestAgenda(
            @PathVariable UUID jobId,
            @RequestBody IngestAgendaCommand command) {
        capabilities.requireAutomaticAgenda();
        return service.ingestAgenda(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void failJob(
            @PathVariable UUID jobId,
            @RequestBody FailJobCommand command) {
        service.failJob(jobId, command);
    }
}
