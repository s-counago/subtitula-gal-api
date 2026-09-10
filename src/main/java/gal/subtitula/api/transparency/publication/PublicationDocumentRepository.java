package gal.subtitula.api.transparency.publication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PublicationDocumentRepository
        extends JpaRepository<PublicationDocument, UUID> {
    List<PublicationDocument> findByPublicationIdOrderByOrdinalAsc(UUID publicationId);
}
