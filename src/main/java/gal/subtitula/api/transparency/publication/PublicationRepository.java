package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.transparency.model.PublicationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PublicationRepository extends JpaRepository<Publication, UUID> {
    Optional<Publication> findFirstByProjectIdOrderByVersionNumberDesc(UUID projectId);
    List<Publication> findByProjectIdOrderByVersionNumberAsc(UUID projectId);
    Optional<Publication> findByIdAndProjectId(UUID id, UUID projectId);
    Optional<Publication> findByPublicSlugAndState(String slug, PublicationState state);
    Optional<Publication> findByPublicSlugAndVersionNumber(String slug, int versionNumber);
    List<Publication> findByStateOrderBySessionDateDescPublishedAtDesc(PublicationState state);
}
