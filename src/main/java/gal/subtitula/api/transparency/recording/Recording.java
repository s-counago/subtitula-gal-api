package gal.subtitula.api.transparency.recording;

import gal.subtitula.api.transparency.model.ProviderSourceState;
import gal.subtitula.api.transparency.model.RecordingUploadState;
import gal.subtitula.api.transparency.model.RecordingVisibility;
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
@Table(name = "recordings")
public class Recording {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "object_key", nullable = false, unique = true, length = 1024)
    private String objectKey;

    @Column(name = "original_source_url", columnDefinition = "text")
    private String originalSourceUrl;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column
    private String etag;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_state", nullable = false)
    private RecordingUploadState uploadState;

    @Column(name = "usage_permission", nullable = false)
    private boolean usagePermission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordingVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_source_state", nullable = false)
    private ProviderSourceState providerSourceState;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Recording() {
    }

    public static Recording createUploadIntent(
            UUID id,
            UUID projectId,
            String objectKey,
            String originalFilename,
            String mimeType,
            long sizeBytes,
            String checksumSha256,
            boolean usagePermission) {
        var recording = new Recording();
        recording.id = id;
        recording.projectId = projectId;
        recording.objectKey = objectKey;
        recording.originalFilename = originalFilename;
        recording.mimeType = mimeType;
        recording.sizeBytes = sizeBytes;
        recording.checksumSha256 = checksumSha256;
        recording.uploadState = RecordingUploadState.INTENT_CREATED;
        recording.usagePermission = usagePermission;
        recording.visibility = RecordingVisibility.PRIVATE;
        recording.providerSourceState = ProviderSourceState.NOT_ISSUED;
        recording.createdAt = Instant.now();
        recording.updatedAt = recording.createdAt;
        return recording;
    }

    public void markUploaded(String etag) {
        if (uploadState == RecordingUploadState.UPLOADED
                || uploadState == RecordingUploadState.VERIFIED) {
            return;
        }
        if (uploadState != RecordingUploadState.INTENT_CREATED
                && uploadState != RecordingUploadState.UPLOADING) {
            throw new IllegalStateException("Recording cannot be uploaded from " + uploadState);
        }
        this.etag = etag;
        uploadState = RecordingUploadState.UPLOADED;
    }

    public void verify(String etag) {
        markUploaded(etag);
        uploadState = RecordingUploadState.VERIFIED;
        verifiedAt = Instant.now();
    }

    public void markProviderSourceActive() {
        if (uploadState != RecordingUploadState.VERIFIED) {
            throw new IllegalStateException("Only verified recordings can be shared with a provider");
        }
        providerSourceState = ProviderSourceState.ACTIVE;
    }

    public void abort() {
        if (uploadState == RecordingUploadState.ABORTED) {
            return;
        }
        if (uploadState == RecordingUploadState.VERIFIED) {
            throw new IllegalStateException("Verified recording cannot be aborted");
        }
        uploadState = RecordingUploadState.ABORTED;
    }

    public void expire() {
        if (uploadState == RecordingUploadState.EXPIRED) {
            return;
        }
        if (uploadState == RecordingUploadState.VERIFIED) {
            throw new IllegalStateException("A verified recording cannot expire");
        }
        uploadState = RecordingUploadState.EXPIRED;
    }

    public void confirmObjectDeleted() {
        if (uploadState == RecordingUploadState.DELETED) {
            return;
        }
        if (uploadState != RecordingUploadState.ABORTED
                && uploadState != RecordingUploadState.EXPIRED) {
            throw new IllegalStateException("Only abandoned recordings can be deleted");
        }
        uploadState = RecordingUploadState.DELETED;
        deletedAt = Instant.now();
    }

    public void publish() {
        if (uploadState != RecordingUploadState.VERIFIED || !usagePermission) {
            throw new IllegalStateException(
                "Only verified recordings with usage permission can be public");
        }
        visibility = RecordingVisibility.PUBLIC;
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

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalSourceUrl() {
        return originalSourceUrl;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public String getEtag() {
        return etag;
    }

    public RecordingUploadState getUploadState() {
        return uploadState;
    }

    public boolean hasUsagePermission() {
        return usagePermission;
    }

    public RecordingVisibility getVisibility() {
        return visibility;
    }

    public ProviderSourceState getProviderSourceState() {
        return providerSourceState;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersion() {
        return version;
    }
}
