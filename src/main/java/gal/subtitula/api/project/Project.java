package gal.subtitula.api.project;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
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

    // Base caption placement; per-segment style/placement overrides. Free-form
    // JSON validated on the client (parseBox/parseSegments).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "base_box", columnDefinition = "jsonb")
    private JsonNode baseBox;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode segments;

    @Column(name = "speed_factor", nullable = false)
    private double speedFactor;

    @Column(name = "workflow_mode", nullable = false)
    private String workflowMode;

    // Set once, when the session is approved. Null while it is still editable.
    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
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
    public JsonNode getBaseBox() { return baseBox; }
    public void setBaseBox(JsonNode b) { this.baseBox = b; }
    public JsonNode getSegments() { return segments; }
    public void setSegments(JsonNode s) { this.segments = s; }
    public double getSpeedFactor() { return speedFactor; }
    public void setSpeedFactor(double f) { this.speedFactor = f; }
    public String getWorkflowMode() { return workflowMode; }
    public Instant getApprovedAt() { return approvedAt; }
    public boolean isApproved() { return approvedAt != null; }
    /** Approval is one-way: a second call must not move the recorded instant. */
    public void approve() { if (approvedAt == null) this.approvedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
