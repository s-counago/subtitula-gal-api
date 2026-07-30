package gal.subtitula.api.transparency.transcript;

import gal.subtitula.api.transparency.model.TranscriptEditKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transcript_edit_events")
public class TranscriptEditEvent {

    @Id
    private UUID id;

    @Column(name = "transcript_revision_id", nullable = false, updatable = false)
    private UUID transcriptRevisionId;

    @Column(name = "segment_id")
    private UUID segmentId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "edit_kind", nullable = false)
    private TranscriptEditKind editKind;

    @Column(name = "prior_content_hash", length = 64)
    private String priorContentHash;

    @Column(name = "new_content_hash", nullable = false, length = 64)
    private String newContentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TranscriptEditEvent() {
    }

    public static TranscriptEditEvent create(
            UUID transcriptRevisionId,
            UUID segmentId,
            UUID actorId,
            TranscriptEditKind editKind,
            String priorContentHash,
            String newContentHash) {
        var event = new TranscriptEditEvent();
        event.id = UUID.randomUUID();
        event.transcriptRevisionId = transcriptRevisionId;
        event.segmentId = segmentId;
        event.actorId = actorId;
        event.editKind = editKind;
        event.priorContentHash = priorContentHash;
        event.newContentHash = newContentHash;
        event.createdAt = Instant.now();
        return event;
    }

    public UUID getId() {
        return id;
    }
}
