package gal.subtitula.api.transparency.transcript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import gal.subtitula.api.transparency.model.EvidenceReviewState;
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
@Table(name = "evidence_segments")
public class EvidenceSegment {

    @Id
    private UUID id;

    @Column(name = "transcript_revision_id", nullable = false, updatable = false)
    private UUID transcriptRevisionId;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "end_ms", nullable = false)
    private long endMs;

    @Column(name = "speaker_id")
    private UUID speakerId;

    @Column(name = "original_text", nullable = false, columnDefinition = "text")
    private String originalText;

    @Column(name = "reviewed_text", nullable = false, columnDefinition = "text")
    private String reviewedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_timings", nullable = false, columnDefinition = "jsonb")
    private JsonNode wordTimings;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_state", nullable = false)
    private EvidenceReviewState reviewState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode signals;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected EvidenceSegment() {
    }

    public static EvidenceSegment create(
            UUID transcriptRevisionId,
            int sequence,
            long startMs,
            long endMs,
            UUID speakerId,
            String text,
            JsonNode wordTimings,
            JsonNode signals) {
        var segment = new EvidenceSegment();
        segment.id = UUID.randomUUID();
        segment.transcriptRevisionId = transcriptRevisionId;
        segment.sequence = sequence;
        segment.startMs = startMs;
        segment.endMs = endMs;
        segment.speakerId = speakerId;
        segment.originalText = text;
        segment.reviewedText = text;
        segment.wordTimings = wordTimings == null ? JsonNodeFactory.instance.arrayNode() : wordTimings;
        segment.reviewState = EvidenceReviewState.UNREVIEWED;
        segment.signals = signals == null ? JsonNodeFactory.instance.objectNode() : signals;
        segment.createdAt = Instant.now();
        segment.updatedAt = segment.createdAt;
        return segment;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void review(String text, UUID speakerId) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Reviewed text cannot be blank");
        }
        this.reviewedText = text.trim();
        this.speakerId = speakerId;
        this.reviewState = EvidenceReviewState.REVIEWED;
    }

    public EvidenceSegment copyToRevision(UUID revisionId, int newSequence) {
        EvidenceSegment copy = create(
            revisionId,
            newSequence,
            startMs,
            endMs,
            speakerId,
            reviewedText,
            wordTimings,
            signals);
        copy.reviewedText = reviewedText;
        copy.reviewState = reviewState;
        return copy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTranscriptRevisionId() {
        return transcriptRevisionId;
    }

    public int getSequence() {
        return sequence;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public UUID getSpeakerId() {
        return speakerId;
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getReviewedText() {
        return reviewedText;
    }

    public JsonNode getWordTimings() {
        return wordTimings;
    }

    public EvidenceReviewState getReviewState() {
        return reviewState;
    }

    public JsonNode getSignals() {
        return signals;
    }

    public long getVersion() {
        return version;
    }
}
