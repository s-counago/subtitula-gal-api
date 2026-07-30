package gal.subtitula.api.transparency.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import gal.subtitula.api.transparency.lifecycle.ProcessingErrorCode;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobState;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_jobs")
public class ProcessingJob {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private ProcessingJobType type;

    @Column(name = "workflow_instance_id")
    private String workflowInstanceId;

    @Column(name = "provider_request_id")
    private String providerRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingJobState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false)
    private ProcessingStage currentStage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "input_hash", length = 64)
    private String inputHash;

    @Column(name = "output_hash", length = 64)
    private String outputHash;

    @Column(name = "input_artifact_key", length = 1024)
    private String inputArtifactKey;

    @Column(name = "output_artifact_key", length = 1024)
    private String outputArtifactKey;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "index_version")
    private String indexVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "safe_error_code")
    private ProcessingErrorCode safeErrorCode;

    @Column(name = "safe_error_message", length = 500)
    private String safeErrorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_usage", nullable = false, columnDefinition = "jsonb")
    private JsonNode providerUsage;

    @Column(name = "cost_microunits")
    private Long costMicrounits;

    @Column(name = "cost_currency", length = 3)
    private String costCurrency;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProcessingJob() {
    }

    public static ProcessingJob queued(
            UUID id,
            UUID projectId,
            ProcessingJobType type,
            String idempotencyKey,
            ProcessingStage initialStage) {
        var job = new ProcessingJob();
        job.id = id;
        job.projectId = projectId;
        job.type = type;
        job.state = ProcessingJobState.QUEUED;
        job.currentStage = initialStage;
        job.idempotencyKey = idempotencyKey;
        job.providerUsage = JsonNodeFactory.instance.objectNode();
        job.createdAt = Instant.now();
        job.updatedAt = job.createdAt;
        return job;
    }

    public static ProcessingJob queued(
            UUID projectId,
            ProcessingJobType type,
            String idempotencyKey,
            ProcessingStage initialStage) {
        return queued(UUID.randomUUID(), projectId, type, idempotencyKey, initialStage);
    }

    public void assignWorkflow(String workflowInstanceId) {
        if (this.workflowInstanceId != null && !this.workflowInstanceId.equals(workflowInstanceId)) {
            if (state != ProcessingJobState.FAILED_RETRYABLE) {
                throw new IllegalStateException("Job already belongs to another Workflow");
            }
        }
        this.workflowInstanceId = workflowInstanceId;
    }

    public void retry(String workflowInstanceId, ProcessingStage stage) {
        if (state != ProcessingJobState.FAILED_RETRYABLE) {
            throw new IllegalStateException("Only retryable failed jobs can retry");
        }
        this.workflowInstanceId = workflowInstanceId;
        this.providerRequestId = null;
        state = ProcessingJobState.RUNNING;
        currentStage = stage;
        attemptCount++;
        safeErrorCode = null;
        safeErrorMessage = null;
        completedAt = null;
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void configure(
            String inputHash,
            String inputArtifactKey,
            String modelVersion,
            String promptVersion,
            String indexVersion) {
        this.inputHash = inputHash;
        this.inputArtifactKey = inputArtifactKey;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.indexVersion = indexVersion;
    }

    public void recordUsage(
            JsonNode providerUsage,
            Long costMicrounits,
            String costCurrency) {
        if (providerUsage != null) {
            this.providerUsage = providerUsage.deepCopy();
        }
        this.costMicrounits = costMicrounits;
        this.costCurrency = costCurrency;
    }

    public void start(ProcessingStage stage) {
        if (state == ProcessingJobState.RUNNING && currentStage == stage) {
            return;
        }
        if (state != ProcessingJobState.QUEUED
                && state != ProcessingJobState.WAITING
                && state != ProcessingJobState.FAILED_RETRYABLE) {
            throw new IllegalStateException("Job cannot start from " + state);
        }
        state = ProcessingJobState.RUNNING;
        currentStage = stage;
        attemptCount++;
        if (startedAt == null) {
            startedAt = Instant.now();
        }
        safeErrorCode = null;
        safeErrorMessage = null;
        completedAt = null;
    }

    public void waitForProvider(String providerRequestId) {
        if (this.providerRequestId != null && !this.providerRequestId.equals(providerRequestId)) {
            throw new IllegalStateException("Job already has another provider request");
        }
        this.providerRequestId = providerRequestId;
        state = ProcessingJobState.WAITING;
        currentStage = ProcessingStage.WAITING_FOR_PROVIDER;
    }

    public void advance(ProcessingStage stage) {
        if (state != ProcessingJobState.RUNNING && state != ProcessingJobState.WAITING) {
            throw new IllegalStateException("Job cannot advance from " + state);
        }
        state = ProcessingJobState.RUNNING;
        currentStage = stage;
    }

    public void succeed(String outputHash, String outputArtifactKey) {
        state = ProcessingJobState.SUCCEEDED;
        currentStage = ProcessingStage.COMPLETED;
        this.outputHash = outputHash;
        this.outputArtifactKey = outputArtifactKey;
        completedAt = Instant.now();
    }

    public void fail(ProcessingErrorCode code, String safeMessage) {
        state = code.isRetryable()
            ? ProcessingJobState.FAILED_RETRYABLE
            : ProcessingJobState.FAILED_TERMINAL;
        safeErrorCode = code;
        safeErrorMessage = safeMessage;
        completedAt = Instant.now();
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public ProcessingJobType getType() {
        return type;
    }

    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public ProcessingJobState getState() {
        return state;
    }

    public ProcessingStage getCurrentStage() {
        return currentStage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getOutputHash() {
        return outputHash;
    }

    public String getInputArtifactKey() {
        return inputArtifactKey;
    }

    public String getOutputArtifactKey() {
        return outputArtifactKey;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getIndexVersion() {
        return indexVersion;
    }

    public ProcessingErrorCode getSafeErrorCode() {
        return safeErrorCode;
    }

    public String getSafeErrorMessage() {
        return safeErrorMessage;
    }

    public JsonNode getProviderUsage() {
        return providerUsage;
    }

    public Long getCostMicrounits() {
        return costMicrounits;
    }

    public String getCostCurrency() {
        return costCurrency;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
