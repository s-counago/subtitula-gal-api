package gal.subtitula.api.transparency.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaItemRepository extends JpaRepository<AgendaItem, UUID> {
    List<AgendaItem> findByProjectIdOrderByOrdinalAsc(UUID projectId);
    Optional<AgendaItem> findByIdAndProjectId(UUID id, UUID projectId);
    void deleteByProjectId(UUID projectId);
}
