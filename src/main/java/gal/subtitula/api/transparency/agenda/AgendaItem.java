package gal.subtitula.api.transparency.agenda;

import gal.subtitula.api.transparency.model.AgendaSource;
import gal.subtitula.api.transparency.model.AgendaVisibility;
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
@Table(name = "agenda_items")
public class AgendaItem {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "external_identifier")
    private String externalIdentifier;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgendaSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgendaVisibility visibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AgendaItem() {
    }

    public static AgendaItem create(
            UUID projectId,
            int ordinal,
            String externalIdentifier,
            String title,
            String description,
            AgendaSource source,
            AgendaVisibility visibility) {
        AgendaItem item = new AgendaItem();
        item.id = UUID.randomUUID();
        item.projectId = projectId;
        item.ordinal = ordinal;
        item.externalIdentifier = externalIdentifier;
        item.title = title;
        item.description = description;
        item.source = source;
        item.visibility = visibility;
        item.createdAt = Instant.now();
        item.updatedAt = item.createdAt;
        return item;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public String getExternalIdentifier() {
        return externalIdentifier;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public AgendaSource getSource() {
        return source;
    }

    public AgendaVisibility getVisibility() {
        return visibility;
    }

    public long getVersion() {
        return version;
    }
}
