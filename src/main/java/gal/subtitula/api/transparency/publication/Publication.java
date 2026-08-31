package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.transparency.model.PublicationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "publications")
public class Publication {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "organization_id", updatable = false)
    private UUID organizationId;

    @Column(name = "public_slug", nullable = false, updatable = false)
    private String publicSlug;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "transcript_revision_id", nullable = false, updatable = false)
    private UUID transcriptRevisionId;

    @Column(name = "guide_id", updatable = false)
    private UUID guideId;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private UUID recordingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_state", nullable = false)
    private PublicationState state;

    @Column(nullable = false, length = 500, updatable = false)
    private String title;

    @Column(name = "language_code", updatable = false)
    private String languageCode;

    @Column(name = "session_date", updatable = false)
    private LocalDate sessionDate;

    @Column(name = "session_body", updatable = false)
    private String sessionBody;

    @Column(updatable = false)
    private String location;

    @Column(name = "session_type", updatable = false)
    private String sessionType;

    @Column(name = "correction_note", length = 1000, updatable = false)
    private String correctionNote;

    @Column(name = "responsible_publisher", updatable = false)
    private UUID responsiblePublisher;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Publication() {
    }

    public static Publication publish(
            Project project,
            String publicSlug,
            int versionNumber,
            UUID transcriptRevisionId,
            UUID guideId,
            UUID recordingId,
            UUID publisherId,
            String correctionNote) {
        Publication publication = new Publication();
        publication.id = UUID.randomUUID();
        publication.projectId = project.getId();
        publication.organizationId = project.getOrganizationId();
        publication.publicSlug = publicSlug;
        publication.versionNumber = versionNumber;
        publication.transcriptRevisionId = transcriptRevisionId;
        publication.guideId = guideId;
        publication.recordingId = recordingId;
        publication.state = PublicationState.PUBLISHED;
        publication.title = project.getName();
        publication.languageCode = project.getLanguage();
        publication.sessionDate = project.getSessionDate();
        publication.sessionBody = project.getSessionBody();
        publication.location = project.getLocation();
        publication.sessionType = project.getSessionType();
        publication.correctionNote = correctionNote;
        publication.responsiblePublisher = publisherId;
        publication.createdAt = Instant.now();
        publication.publishedAt = publication.createdAt;
        return publication;
    }

    public void supersede() {
        if (state != PublicationState.PUBLISHED) {
            throw new IllegalStateException("Only a published snapshot can be superseded");
        }
        state = PublicationState.SUPERSEDED;
        supersededAt = Instant.now();
    }

    public void withdraw() {
        if (state == PublicationState.WITHDRAWN) {
            return;
        }
        if (state != PublicationState.PUBLISHED) {
            throw new IllegalStateException("Only a published snapshot can be withdrawn");
        }
        state = PublicationState.WITHDRAWN;
        withdrawnAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getPublicSlug() {
        return publicSlug;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public UUID getTranscriptRevisionId() {
        return transcriptRevisionId;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public UUID getRecordingId() {
        return recordingId;
    }

    public PublicationState getState() {
        return state;
    }

    public String getTitle() {
        return title;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public String getSessionBody() {
        return sessionBody;
    }

    public String getLocation() {
        return location;
    }

    public String getSessionType() {
        return sessionType;
    }

    public String getCorrectionNote() {
        return correctionNote;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public long getVersion() {
        return version;
    }
}
