package gal.subtitula.api.transparency.internal;

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
import gal.subtitula.api.transparency.internal.dto.JobContextResponse;
import gal.subtitula.api.transparency.internal.dto.ProviderSubmittedCommand;
import gal.subtitula.api.transparency.internal.dto.RetryIngestCommand;
import gal.subtitula.api.transparency.internal.dto.StartJobCommand;
import gal.subtitula.api.transparency.internal.dto.StartEnrichmentCommand;
import gal.subtitula.api.transparency.internal.dto.UploadIntentInternalResponse;
import gal.subtitula.api.transparency.internal.dto.WebhookReceivedCommand;
import gal.subtitula.api.transparency.internal.dto.WebhookReceivedResponse;
import org.springframework.http.HttpStatus;
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

    public InternalProcessingController(InternalProcessingService service) {
        this.service = service;
    }

    @PostMapping("/upload-intents")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadIntentInternalResponse createUploadIntent(
            @RequestBody CreateUploadIntentCommand command) {
        return service.createUploadIntent(command);
    }

    @PostMapping("/upload-intents/{intentId}/complete")
    public UploadIntentInternalResponse completeUpload(
            @PathVariable UUID intentId,
            @RequestBody CompleteUploadCommand command) {
        return service.completeUpload(intentId, command);
    }

    @PostMapping("/upload-intents/{intentId}/abort")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abortUpload(@PathVariable UUID intentId) {
        service.abortUpload(intentId);
    }

    @PostMapping("/cleanup/upload-intents/candidates")
    public List<ExpiredUploadCandidateResponse> expiredUploadCandidates(
            @RequestBody ExpiredUploadCandidatesCommand command) {
        return service.expiredUploadCandidates(command);
    }

    @PostMapping("/cleanup/upload-intents/{intentId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finalizeExpiredUpload(
            @PathVariable UUID intentId,
            @RequestBody FinalizeExpiredUploadCommand command) {
        service.finalizeExpiredUpload(intentId, command);
    }

    @GetMapping("/jobs/{jobId}/context")
    public JobContextResponse jobContext(@PathVariable UUID jobId) {
        return service.jobContext(jobId);
    }

    @GetMapping("/jobs/{jobId}/enrichment-context")
    public EnrichmentContextResponse enrichmentContext(@PathVariable UUID jobId) {
        return service.enrichmentContext(jobId);
    }

    @PostMapping("/jobs/{jobId}/enrichment-start")
    public EnrichmentStartResponse startEnrichment(
            @PathVariable UUID jobId,
            @RequestBody StartEnrichmentCommand command) {
        return service.startEnrichment(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/start")
    public JobContextResponse startJob(
            @PathVariable UUID jobId,
            @RequestBody StartJobCommand command) {
        return service.startJob(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/ingest-retry")
    public JobContextResponse retryIngest(
            @PathVariable UUID jobId,
            @RequestBody RetryIngestCommand command) {
        return service.retryIngest(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/provider-submitted")
    public JobContextResponse providerSubmitted(
            @PathVariable UUID jobId,
            @RequestBody ProviderSubmittedCommand command) {
        return service.providerSubmitted(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/webhook-received")
    public WebhookReceivedResponse webhookReceived(
            @PathVariable UUID jobId,
            @RequestBody WebhookReceivedCommand command) {
        return service.webhookReceived(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/transcript")
    public IngestTranscriptResponse ingestTranscript(
            @PathVariable UUID jobId,
            @RequestBody IngestTranscriptCommand command) {
        return service.ingestTranscript(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/guide")
    public IngestGuideResponse ingestGuide(
            @PathVariable UUID jobId,
            @RequestBody IngestGuideCommand command) {
        return service.ingestGuide(jobId, command);
    }

    @PostMapping("/jobs/{jobId}/failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void failJob(
            @PathVariable UUID jobId,
            @RequestBody FailJobCommand command) {
        service.failJob(jobId, command);
    }
}
