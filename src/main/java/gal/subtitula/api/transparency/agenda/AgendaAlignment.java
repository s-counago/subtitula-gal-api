package gal.subtitula.api.transparency.agenda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import gal.subtitula.api.transparency.model.AgendaAlignmentState;
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
@Table(name = "agenda_alignments")
public class AgendaAlignment {

    @Id
    private UUID id;

    @Column(name = "transcript_revision_id", nullable = false, updatable = false)
    private UUID transcriptRevisionId;

    @Column(name = "agenda_item_id", nullable = false, updatable = false)
    private UUID agendaItemId;

    @Column(nullable = false)
    private int occurrence;

    @Column(name = "start_segment_id", nullable = false)
    private UUID startSegmentId;

    @Column(name = "end_segment_id", nullable = false)
    private UUID endSegmentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode signals;

    @Enumerated(EnumType.STRING)
    @Column(name = "alignment_state", nullable = false)
    private AgendaAlignmentState state;

    @Column(name = "requires_human_check", nullable = false)
    private boolean requiresHumanCheck;

    @Column(name = "algorithm_version", nullable = false)
    private String algorithmVersion;

    @Column(nullable = false)
    private boolean revisited;

    @Column(name = "checked_by")
    private UUID checkedBy;

    @Column(name = "checked_at")
    private Instant checkedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AgendaAlignment() {
    }

    public static AgendaAlignment create(
            UUID revisionId,
            UUID agendaItemId,
            int occurrence,
            UUID startSegmentId,
            UUID endSegmentId,
            JsonNode signals,
            AgendaAlignmentState state,
            boolean requiresHumanCheck,
            String algorithmVersion,
            boolean revisited) {
        AgendaAlignment value = new AgendaAlignment();
        value.id = UUID.randomUUID();
        value.transcriptRevisionId = revisionId;
        value.agendaItemId = agendaItemId;
        value.occurrence = occurrence;
        value.startSegmentId = startSegmentId;
        value.endSegmentId = endSegmentId;
        value.signals = signals == null ? JsonNodeFactory.instance.objectNode() : signals;
        value.state = state;
        value.requiresHumanCheck = requiresHumanCheck;
        value.algorithmVersion = algorithmVersion;
        value.revisited = revisited;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void confirm(UUID actorId) {
        state = AgendaAlignmentState.CONFIRMED;
        requiresHumanCheck = false;
        checkedBy = actorId;
        checkedAt = Instant.now();
    }

    public void adjust(UUID startSegmentId, UUID endSegmentId, UUID actorId) {
        this.startSegmentId = startSegmentId;
        this.endSegmentId = endSegmentId;
        state = AgendaAlignmentState.ADJUSTED;
        requiresHumanCheck = false;
        checkedBy = actorId;
        checkedAt = Instant.now();
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTranscriptRevisionId() {
        return transcriptRevisionId;
    }

    public UUID getAgendaItemId() {
        return agendaItemId;
    }

    public int getOccurrence() {
        return occurrence;
    }

    public UUID getStartSegmentId() {
        return startSegmentId;
    }

    public UUID getEndSegmentId() {
        return endSegmentId;
    }

    public JsonNode getSignals() {
        return signals;
    }

    public AgendaAlignmentState getState() {
        return state;
    }

    public boolean isRequiresHumanCheck() {
        return requiresHumanCheck;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public boolean isRevisited() {
        return revisited;
    }

    public UUID getCheckedBy() {
        return checkedBy;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public long getVersion() {
        return version;
    }
}
