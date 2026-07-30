package gal.subtitula.api.transparency.transcript;

import gal.subtitula.api.transparency.model.TranscriptRevisionSource;
import gal.subtitula.api.transparency.model.TranscriptRevisionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transcript_revisions")
public class TranscriptRevision {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "parent_revision_id")
    private UUID parentRevisionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranscriptRevisionSource source;

    @Column
    private String provider;

    @Column
    private String model;

    @Column(name = "language_code")
    private String languageCode;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "keyterm_version")
    private String keytermVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TranscriptRevisionState state;

    @Column(name = "raw_artifact_key", length = 1024)
    private String rawArtifactKey;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "frozen_by")
    private UUID frozenBy;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected TranscriptRevision() {
    }

    public static TranscriptRevision createAsr(
            UUID projectId,
            int versionNumber,
            String provider,
            String model,
            String languageCode,
            String keytermVersion,
            String rawArtifactKey,
            String contentHash) {
        var revision = new TranscriptRevision();
        revision.id = UUID.randomUUID();
        revision.projectId = projectId;
        revision.versionNumber = versionNumber;
        revision.source = TranscriptRevisionSource.ASR;
        revision.provider = provider;
        revision.model = model;
        revision.languageCode = languageCode;
        revision.keytermVersion = keytermVersion;
        revision.state = TranscriptRevisionState.WORKING;
        revision.rawArtifactKey = rawArtifactKey;
        revision.contentHash = contentHash;
        revision.createdAt = Instant.now();
        return revision;
    }

    public void freeze(UUID actorId) {
        freeze(actorId, contentHash);
    }

    public void freeze(UUID actorId, String reviewedContentHash) {
        if (state == TranscriptRevisionState.FROZEN) {
            return;
        }
        if (state != TranscriptRevisionState.WORKING) {
            throw new IllegalStateException("Only a working revision can be frozen");
        }
        state = TranscriptRevisionState.FROZEN;
        contentHash = reviewedContentHash;
        frozenBy = actorId;
        frozenAt = Instant.now();
    }

    public static TranscriptRevision createCorrection(
            UUID projectId,
            int versionNumber,
            UUID parentRevisionId,
            String languageCode,
            String contentHash,
            UUID actorId) {
        TranscriptRevision revision = new TranscriptRevision();
        revision.id = UUID.randomUUID();
        revision.projectId = projectId;
        revision.versionNumber = versionNumber;
        revision.parentRevisionId = parentRevisionId;
        revision.source = TranscriptRevisionSource.CORRECTION;
        revision.languageCode = languageCode;
        revision.state = TranscriptRevisionState.WORKING;
        revision.contentHash = contentHash;
        revision.createdBy = actorId;
        revision.createdAt = Instant.now();
        return revision;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public UUID getParentRevisionId() {
        return parentRevisionId;
    }

    public TranscriptRevisionSource getSource() {
        return source;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getKeytermVersion() {
        return keytermVersion;
    }

    public TranscriptRevisionState getState() {
        return state;
    }

    public String getRawArtifactKey() {
        return rawArtifactKey;
    }

    public String getContentHash() {
        return contentHash;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getFrozenBy() {
        return frozenBy;
    }

    public Instant getFrozenAt() {
        return frozenAt;
    }

    public long getVersion() {
        return version;
    }
}
