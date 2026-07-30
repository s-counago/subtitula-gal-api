package gal.subtitula.api.transparency.guide;

import gal.subtitula.api.transparency.model.ContributionKind;
import gal.subtitula.api.transparency.model.GuideGenerationState;
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
@Table(name = "guide_contributions")
public class GuideContribution {

    @Id
    private UUID id;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "speaker_id")
    private UUID speakerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_kind", nullable = false)
    private ContributionKind kind;

    @Column(name = "neutral_summary", nullable = false, columnDefinition = "text")
    private String neutralSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_state", nullable = false)
    private GuideGenerationState generationState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected GuideContribution() {
    }

    public static GuideContribution create(
            UUID id,
            UUID topicId,
            int ordinal,
            UUID speakerId,
            ContributionKind kind,
            String summary) {
        GuideContribution value = new GuideContribution();
        value.id = id;
        value.topicId = topicId;
        value.ordinal = ordinal;
        value.speakerId = speakerId;
        value.kind = kind;
        value.neutralSummary = summary;
        value.generationState = GuideGenerationState.AUTOMATIC;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public UUID getSpeakerId() {
        return speakerId;
    }

    public ContributionKind getKind() {
        return kind;
    }

    public String getNeutralSummary() {
        return neutralSummary;
    }

    public GuideGenerationState getGenerationState() {
        return generationState;
    }

    public long getVersion() {
        return version;
    }
}
