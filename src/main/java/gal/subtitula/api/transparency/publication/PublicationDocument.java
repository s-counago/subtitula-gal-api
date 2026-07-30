package gal.subtitula.api.transparency.publication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "publication_documents")
public class PublicationDocument {

    @Id
    private UUID id;

    @Column(name = "publication_id", nullable = false, updatable = false)
    private UUID publicationId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(nullable = false, updatable = false)
    private int ordinal;

    protected PublicationDocument() {
    }

    public static PublicationDocument create(
            UUID publicationId,
            UUID documentId,
            int ordinal) {
        PublicationDocument value = new PublicationDocument();
        value.id = UUID.randomUUID();
        value.publicationId = publicationId;
        value.documentId = documentId;
        value.ordinal = ordinal;
        return value;
    }

    public UUID getPublicationId() {
        return publicationId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getOrdinal() {
        return ordinal;
    }
}
