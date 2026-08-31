package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.transparency.model.DocumentProvenance;
import gal.subtitula.api.transparency.model.DocumentVisibility;
import gal.subtitula.api.transparency.model.ProjectDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_documents")
public class ProjectDocument {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private ProjectDocumentType type;

    @Column(name = "official_url", columnDefinition = "text")
    private String officialUrl;

    @Column(name = "private_object_key", length = 1024)
    private String privateObjectKey;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "issuing_body", length = 500)
    private String issuingBody;

    @Column(name = "document_date")
    private LocalDate documentDate;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentProvenance provenance;

    @Column(name = "publication_permission", nullable = false)
    private boolean publicationPermission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProjectDocument() {
    }

    public static ProjectDocument official(
            UUID projectId,
            ProjectDocumentType type,
            String officialUrl,
            String title,
            String issuingBody,
            LocalDate documentDate,
            boolean publicationPermission) {
        ProjectDocument document = new ProjectDocument();
        document.id = UUID.randomUUID();
        document.projectId = projectId;
        document.type = type;
        document.officialUrl = officialUrl;
        document.title = title;
        document.issuingBody = issuingBody;
        document.documentDate = documentDate;
        document.visibility = publicationPermission
            ? DocumentVisibility.PUBLIC
            : DocumentVisibility.PRIVATE;
        document.provenance = DocumentProvenance.OFFICIAL_URL;
        document.publicationPermission = publicationPermission;
        document.createdAt = Instant.now();
        document.updatedAt = document.createdAt;
        return document;
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

    public ProjectDocumentType getType() {
        return type;
    }

    public String getOfficialUrl() {
        return officialUrl;
    }

    public String getPrivateObjectKey() {
        return privateObjectKey;
    }

    public String getTitle() {
        return title;
    }

    public String getIssuingBody() {
        return issuingBody;
    }

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public DocumentVisibility getVisibility() {
        return visibility;
    }

    public DocumentProvenance getProvenance() {
        return provenance;
    }

    public boolean hasPublicationPermission() {
        return publicationPermission;
    }

    public long getVersion() {
        return version;
    }
}
