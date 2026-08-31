package gal.subtitula.api.transparency.ingest;

import gal.subtitula.api.transparency.model.UploadIntentState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_intents")
public class UploadIntent {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "recording_id", nullable = false, updatable = false, unique = true)
    private UUID recordingId;

    @Column(name = "processing_job_id", nullable = false, updatable = false, unique = true)
    private UUID processingJobId;

    @Column(name = "client_request_id", nullable = false, updatable = false)
    private UUID clientRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadIntentState state;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "aborted_at")
    private Instant abortedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UploadIntent() {
    }

    public static UploadIntent create(
            UUID id,
            UUID projectId,
            UUID recordingId,
            UUID processingJobId,
            UUID clientRequestId,
            Instant expiresAt) {
        var intent = new UploadIntent();
        intent.id = id;
        intent.projectId = projectId;
        intent.recordingId = recordingId;
        intent.processingJobId = processingJobId;
        intent.clientRequestId = clientRequestId;
        intent.state = UploadIntentState.CREATED;
        intent.expiresAt = expiresAt;
        intent.createdAt = Instant.now();
        intent.updatedAt = intent.createdAt;
        return intent;
    }

    public boolean isExpired(Instant now) {
        return state == UploadIntentState.CREATED && !expiresAt.isAfter(now);
    }

    public void complete() {
        if (state == UploadIntentState.COMPLETED) {
            return;
        }
        if (state != UploadIntentState.CREATED) {
            throw new IllegalStateException("Upload intent cannot be completed from " + state);
        }
        state = UploadIntentState.COMPLETED;
        completedAt = Instant.now();
    }

    public void abort() {
        if (state == UploadIntentState.ABORTED) {
            return;
        }
        if (state != UploadIntentState.CREATED) {
            throw new IllegalStateException("Upload intent cannot be aborted from " + state);
        }
        state = UploadIntentState.ABORTED;
        abortedAt = Instant.now();
    }

    public void expire() {
        if (state == UploadIntentState.CREATED) {
            state = UploadIntentState.EXPIRED;
        }
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getRecordingId() { return recordingId; }
    public UUID getProcessingJobId() { return processingJobId; }
    public UUID getClientRequestId() { return clientRequestId; }
    public UploadIntentState getState() { return state; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getAbortedAt() { return abortedAt; }
    public long getVersion() { return version; }
}
