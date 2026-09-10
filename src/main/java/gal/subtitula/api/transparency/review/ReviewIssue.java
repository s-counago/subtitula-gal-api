package gal.subtitula.api.transparency.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import gal.subtitula.api.transparency.model.ReviewIssueSeverity;
import gal.subtitula.api.transparency.model.ReviewIssueState;
import gal.subtitula.api.transparency.model.ReviewIssueType;
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
@Table(name = "review_issues")
public class ReviewIssue {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "transcript_revision_id", nullable = false, updatable = false)
    private UUID transcriptRevisionId;

    @Column(name = "segment_id")
    private UUID segmentId;

    @Column(name = "speaker_id")
    private UUID speakerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false)
    private ReviewIssueType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewIssueSeverity severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode signals;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewIssueState state;

    @Column
    private String resolution;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ReviewIssue() {
    }

    public static ReviewIssue open(
            UUID projectId,
            UUID transcriptRevisionId,
            UUID segmentId,
            UUID speakerId,
            ReviewIssueType type,
            ReviewIssueSeverity severity,
            JsonNode signals) {
        var issue = new ReviewIssue();
        issue.id = UUID.randomUUID();
        issue.projectId = projectId;
        issue.transcriptRevisionId = transcriptRevisionId;
        issue.segmentId = segmentId;
        issue.speakerId = speakerId;
        issue.type = type;
        issue.severity = severity;
        issue.signals = signals == null ? JsonNodeFactory.instance.objectNode() : signals;
        issue.state = ReviewIssueState.OPEN;
        issue.createdAt = Instant.now();
        issue.updatedAt = issue.createdAt;
        return issue;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void resolve(String resolution, UUID actorId) {
        if (state != ReviewIssueState.OPEN) {
            return;
        }
        this.state = ReviewIssueState.RESOLVED;
        this.resolution = resolution;
        this.resolvedBy = actorId;
        this.resolvedAt = Instant.now();
    }

    public void dismiss(String resolution, UUID actorId) {
        if (state != ReviewIssueState.OPEN) {
            return;
        }
        this.state = ReviewIssueState.DISMISSED;
        this.resolution = resolution;
        this.resolvedBy = actorId;
        this.resolvedAt = Instant.now();
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

    public UUID getSegmentId() {
        return segmentId;
    }

    public UUID getSpeakerId() {
        return speakerId;
    }

    public ReviewIssueType getType() {
        return type;
    }

    public ReviewIssueSeverity getSeverity() {
        return severity;
    }

    public JsonNode getSignals() {
        return signals;
    }

    public ReviewIssueState getState() {
        return state;
    }

    public String getResolution() {
        return resolution;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public long getVersion() {
        return version;
    }
}
