package gal.subtitula.api.transparency.transcript;

import gal.subtitula.api.transparency.model.SpeakerIdentityState;
import gal.subtitula.api.transparency.model.SpeakerSource;
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
@Table(name = "speakers")
public class Speaker {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "provider_label")
    private String providerLabel;

    @Column(name = "display_label", nullable = false)
    private String displayLabel;

    @Column(name = "confirmed_name")
    private String confirmedName;

    @Column(name = "speaker_role")
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_state", nullable = false)
    private SpeakerIdentityState identityState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpeakerSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Speaker() {
    }

    public static Speaker createUnknown(UUID projectId, String providerLabel) {
        var speaker = new Speaker();
        speaker.id = UUID.randomUUID();
        speaker.projectId = projectId;
        speaker.providerLabel = providerLabel;
        speaker.displayLabel = "Persoa non identificada";
        speaker.identityState = SpeakerIdentityState.UNKNOWN;
        speaker.source = SpeakerSource.ASR;
        speaker.createdAt = Instant.now();
        speaker.updatedAt = speaker.createdAt;
        return speaker;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void reviewIdentity(String confirmedName, String role) {
        this.role = role == null || role.isBlank() ? null : role.trim();
        if (confirmedName == null || confirmedName.isBlank()) {
            this.confirmedName = null;
            this.displayLabel = "Persoa non identificada";
            this.identityState = SpeakerIdentityState.UNKNOWN;
            return;
        }
        this.confirmedName = confirmedName.trim();
        this.displayLabel = this.confirmedName;
        this.identityState = SpeakerIdentityState.CONFIRMED;
        this.source = SpeakerSource.HUMAN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getProviderLabel() {
        return providerLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getConfirmedName() {
        return confirmedName;
    }

    public String getRole() {
        return role;
    }

    public SpeakerIdentityState getIdentityState() {
        return identityState;
    }

    public SpeakerSource getSource() {
        return source;
    }

    public long getVersion() {
        return version;
    }
}
