package gal.subtitula.api.transparency.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaAlignmentRepository extends JpaRepository<AgendaAlignment, UUID> {
    List<AgendaAlignment> findByTranscriptRevisionIdOrderByOccurrenceAsc(UUID revisionId);
    Optional<AgendaAlignment> findByIdAndTranscriptRevisionId(UUID id, UUID revisionId);
    void deleteByTranscriptRevisionId(UUID revisionId);
    long countByTranscriptRevisionIdAndRequiresHumanCheckTrue(UUID revisionId);
}
