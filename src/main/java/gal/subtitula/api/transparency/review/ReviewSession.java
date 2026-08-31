package gal.subtitula.api.transparency.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_sessions")
public class ReviewSession {

    private static final long MAX_ACTIVE_GAP_SECONDS = 120;

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "transcript_revision_id", nullable = false, updatable = false)
    private UUID transcriptRevisionId;

    @Column(name = "reviewer_id", nullable = false, updatable = false)
    private UUID reviewerId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "active_duration_ms", nullable = false)
    private long activeDurationMs;

    @Column(name = "resolved_count", nullable = false)
    private int resolvedCount;

    @Column(name = "dismissed_count", nullable = false)
    private int dismissedCount;

    @Column(name = "manual_edit_count", nullable = false)
    private int manualEditCount;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ReviewSession() {
    }

    public static ReviewSession open(
            UUID projectId,
            UUID transcriptRevisionId,
            UUID reviewerId) {
        ReviewSession session = new ReviewSession();
        session.id = UUID.randomUUID();
        session.projectId = projectId;
        session.transcriptRevisionId = transcriptRevisionId;
        session.reviewerId = reviewerId;
        session.startedAt = Instant.now();
        session.lastActivityAt = session.startedAt;
        return session;
    }

    public void recordActivity(Instant now) {
        if (completedAt != null || now.isBefore(lastActivityAt)) {
            return;
        }
        long gap = Duration.between(lastActivityAt, now).toMillis();
        if (gap <= MAX_ACTIVE_GAP_SECONDS * 1000) {
            activeDurationMs += gap;
        }
        lastActivityAt = now;
    }

    public void recordResolution(boolean dismissed, Instant now) {
        recordActivity(now);
        if (dismissed) {
            dismissedCount++;
        } else {
            resolvedCount++;
        }
    }

    public void recordManualEdit(Instant now) {
        recordActivity(now);
        manualEditCount++;
    }

    public void complete(Instant now) {
        recordActivity(now);
        if (completedAt == null) {
            completedAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getTranscriptRevisionId() { return transcriptRevisionId; }
    public UUID getReviewerId() { return reviewerId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public long getActiveDurationMs() { return activeDurationMs; }
    public int getResolvedCount() { return resolvedCount; }
    public int getDismissedCount() { return dismissedCount; }
    public int getManualEditCount() { return manualEditCount; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version; }
}
