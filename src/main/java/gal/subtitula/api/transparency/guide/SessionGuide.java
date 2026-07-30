package gal.subtitula.api.transparency.guide;

import gal.subtitula.api.transparency.model.GuideState;
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
@Table(name = "session_guides")
public class SessionGuide {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "transcript_revision_id", nullable = false, updatable = false)
    private UUID transcriptRevisionId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @Column(name = "generation_model", nullable = false)
    private String generationModel;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "guide_state", nullable = false)
    private GuideState state;

    @Column(name = "generator_content_hash", nullable = false, length = 64)
    private String generatorContentHash;

    @Column(name = "raw_artifact_key", length = 1024)
    private String rawArtifactKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SessionGuide() {
    }

    public static SessionGuide create(
            UUID projectId,
            UUID revisionId,
            int versionNumber,
            String schemaVersion,
            String model,
            String promptVersion,
            String contentHash,
            String rawArtifactKey,
            boolean hasExceptions) {
        SessionGuide guide = new SessionGuide();
        guide.id = UUID.randomUUID();
        guide.projectId = projectId;
        guide.transcriptRevisionId = revisionId;
        guide.versionNumber = versionNumber;
        guide.schemaVersion = schemaVersion;
        guide.generationModel = model;
        guide.promptVersion = promptVersion;
        guide.state = hasExceptions ? GuideState.EXCEPTIONS : GuideState.READY;
        guide.generatorContentHash = contentHash;
        guide.rawArtifactKey = rawArtifactKey;
        guide.createdAt = Instant.now();
        return guide;
    }

    public void markReady(UUID actorId) {
        state = GuideState.READY;
        confirmedBy = actorId;
        confirmedAt = Instant.now();
    }

    public void markPublished() {
        if (state != GuideState.READY) {
            throw new IllegalStateException("Only a ready guide can be published");
        }
        state = GuideState.PUBLISHED;
    }

    public void supersede() {
        state = GuideState.SUPERSEDED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getTranscriptRevisionId() {
        return transcriptRevisionId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getGenerationModel() {
        return generationModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public GuideState getState() {
        return state;
    }

    public String getGeneratorContentHash() {
        return generatorContentHash;
    }

    public String getRawArtifactKey() {
        return rawArtifactKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getConfirmedBy() {
        return confirmedBy;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public long getVersion() {
        return version;
    }
}
