package gal.subtitula.api.transparency.guide;

import com.fasterxml.jackson.databind.JsonNode;
import gal.subtitula.api.transparency.model.DecisionStatus;
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
@Table(name = "guide_decisions")
public class GuideDecision {

    @Id
    private UUID id;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Column(name = "agenda_item_id")
    private UUID agendaItemId;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "neutral_description", nullable = false, columnDefinition = "text")
    private String neutralDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false)
    private DecisionStatus status;

    @Column(columnDefinition = "text")
    private String motion;

    @Column(length = 500)
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vote_details", columnDefinition = "jsonb")
    private JsonNode voteDetails;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected GuideDecision() {
    }

    public static GuideDecision candidate(
            UUID id,
            UUID topicId,
            UUID agendaItemId,
            int ordinal,
            String description,
            String motion,
            String result,
            JsonNode voteDetails) {
        GuideDecision decision = new GuideDecision();
        decision.id = id;
        decision.topicId = topicId;
        decision.agendaItemId = agendaItemId;
        decision.ordinal = ordinal;
        decision.neutralDescription = description;
        decision.status = DecisionStatus.CANDIDATE;
        decision.motion = motion;
        decision.result = result;
        decision.voteDetails = voteDetails;
        decision.createdAt = Instant.now();
        decision.updatedAt = decision.createdAt;
        return decision;
    }

    public void confirm(UUID actorId, String description) {
        if (status == DecisionStatus.OMITTED) {
            throw new IllegalStateException("An omitted decision cannot be confirmed");
        }
        if (description != null && !description.isBlank()) {
            neutralDescription = description.trim();
        }
        status = DecisionStatus.CONFIRMED;
        confirmedBy = actorId;
        confirmedAt = Instant.now();
    }

    public void omit(UUID actorId) {
        status = DecisionStatus.OMITTED;
        confirmedBy = actorId;
        confirmedAt = Instant.now();
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

    public UUID getAgendaItemId() {
        return agendaItemId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getNeutralDescription() {
        return neutralDescription;
    }

    public DecisionStatus getStatus() {
        return status;
    }

    public String getMotion() {
        return motion;
    }

    public String getResult() {
        return result;
    }

    public JsonNode getVoteDetails() {
        return voteDetails;
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
