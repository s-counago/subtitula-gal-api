package gal.subtitula.api.transparency.transcript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TranscriptRevisionRepository extends JpaRepository<TranscriptRevision, UUID> {
    Optional<TranscriptRevision> findFirstByProjectIdOrderByVersionNumberDesc(UUID projectId);
    Optional<TranscriptRevision> findByIdAndProjectId(UUID id, UUID projectId);
    Optional<TranscriptRevision> findByProjectIdAndContentHash(UUID projectId, String contentHash);
}
