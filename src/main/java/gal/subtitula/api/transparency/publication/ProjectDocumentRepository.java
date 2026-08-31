package gal.subtitula.api.transparency.publication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {
    List<ProjectDocument> findByProjectIdOrderByDocumentDateAscTitleAsc(UUID projectId);
    List<ProjectDocument> findByIdInAndProjectId(Collection<UUID> ids, UUID projectId);
}
