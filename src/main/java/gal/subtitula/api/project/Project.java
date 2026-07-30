package gal.subtitula.api.project;

import com.fasterxml.jackson.databind.JsonNode;
import gal.subtitula.api.transparency.lifecycle.InstitutionalProjectStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column
    private String language;

    @Column(name = "duration_sec", nullable = false)
    private double durationSec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Word> words;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode style;

    @Column(name = "speed_factor", nullable = false)
    private double speedFactor;

    @Column(name = "workflow_mode", nullable = false)
    private String workflowMode;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstitutionalProjectStatus status;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(name = "session_body")
    private String sessionBody;

    @Column
    private String location;

    @Column(name = "session_type")
    private String sessionType;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Project() {}

    public static Project create(UUID userId, String name, String language,
                                 double durationSec, List<Word> words, JsonNode style, String workflowMode) {
        Project p = new Project();
        p.id = UUID.randomUUID();
        p.userId = userId;
        p.name = name;
        p.language = language;
        p.durationSec = durationSec;
        p.words = words == null ? List.of() : words;
        p.style = style;
        p.speedFactor = 1.0;
        p.workflowMode = workflowMode;
        p.status = "institution".equals(workflowMode)
            ? InstitutionalProjectStatus.REVIEW_REQUIRED
            : InstitutionalProjectStatus.READY;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public static Project createInstitutionalDraft(
            UUID userId,
            String name,
            String language,
            LocalDate sessionDate,
            String sessionBody,
            String location,
            String sessionType) {
        Project project = create(
            userId,
            name,
            language,
            0,
            List.of(),
            null,
            "institution");
        project.status = InstitutionalProjectStatus.DRAFT;
        project.sessionDate = sessionDate;
        project.sessionBody = sessionBody;
        project.location = location;
        project.sessionType = sessionType;
        return project;
    }

    public void transitionTo(InstitutionalProjectStatus target) {
        if (status == target) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid project transition " + status + " -> " + target);
        }
        status = target;
        if (target != InstitutionalProjectStatus.PROCESSING_FAILED) {
            failureCode = null;
            failureMessage = null;
        }
    }

    public void fail(String code, String safeMessage) {
        if (status != InstitutionalProjectStatus.PROCESSING_FAILED) {
            if (status == InstitutionalProjectStatus.ARCHIVED
                    || status == InstitutionalProjectStatus.PUBLISHED
                    || status == InstitutionalProjectStatus.READY
                    || status == InstitutionalProjectStatus.REVIEW_REQUIRED) {
                throw new IllegalStateException("Project cannot fail from " + status);
            }
            status = InstitutionalProjectStatus.PROCESSING_FAILED;
        }
        failureCode = code;
        failureMessage = safeMessage;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getLanguage() { return language; }
    public void setLanguage(String l) { this.language = l; }
    public double getDurationSec() { return durationSec; }
    public void setDurationSec(double d) { this.durationSec = d; }
    public List<Word> getWords() { return words; }
    public void setWords(List<Word> w) { this.words = w == null ? List.of() : w; }
    public JsonNode getStyle() { return style; }
    public void setStyle(JsonNode s) { this.style = s; }
    public double getSpeedFactor() { return speedFactor; }
    public void setSpeedFactor(double f) { this.speedFactor = f; }
    public String getWorkflowMode() { return workflowMode; }
    public UUID getOrganizationId() { return organizationId; }
    public InstitutionalProjectStatus getStatus() { return status; }
    public LocalDate getSessionDate() { return sessionDate; }
    public String getSessionBody() { return sessionBody; }
    public String getLocation() { return location; }
    public String getSessionType() { return sessionType; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
