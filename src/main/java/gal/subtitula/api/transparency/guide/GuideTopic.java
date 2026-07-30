package gal.subtitula.api.transparency.guide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import gal.subtitula.api.transparency.model.GuideGenerationState;
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
@Table(name = "guide_topics")
public class GuideTopic {

    @Id
    private UUID id;

    @Column(name = "guide_id", nullable = false, updatable = false)
    private UUID guideId;

    @Column(nullable = false)
    private int ordinal;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "neutral_summary", nullable = false, columnDefinition = "text")
    private String neutralSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode aliases;

    @Column(name = "agenda_item_id")
    private UUID agendaItemId;

    @Column(name = "start_segment_id", nullable = false)
    private UUID startSegmentId;

    @Column(name = "end_segment_id", nullable = false)
    private UUID endSegmentId;

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

    protected GuideTopic() {
    }

    public static GuideTopic create(
            UUID id,
            UUID guideId,
            int ordinal,
            String title,
            String summary,
            JsonNode aliases,
            UUID agendaItemId,
            UUID startSegmentId,
            UUID endSegmentId) {
        GuideTopic topic = new GuideTopic();
        topic.id = id;
        topic.guideId = guideId;
        topic.ordinal = ordinal;
        topic.title = title;
        topic.neutralSummary = summary;
        topic.aliases = aliases == null ? JsonNodeFactory.instance.arrayNode() : aliases;
        topic.agendaItemId = agendaItemId;
        topic.startSegmentId = startSegmentId;
        topic.endSegmentId = endSegmentId;
        topic.generationState = GuideGenerationState.AUTOMATIC;
        topic.createdAt = Instant.now();
        topic.updatedAt = topic.createdAt;
        return topic;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getTitle() {
        return title;
    }

    public String getNeutralSummary() {
        return neutralSummary;
    }

    public JsonNode getAliases() {
        return aliases;
    }

    public UUID getAgendaItemId() {
        return agendaItemId;
    }

    public UUID getStartSegmentId() {
        return startSegmentId;
    }

    public UUID getEndSegmentId() {
        return endSegmentId;
    }

    public GuideGenerationState getGenerationState() {
        return generationState;
    }

    public long getVersion() {
        return version;
    }
}
