package gal.subtitula.api.transparency.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionGuideRepository extends JpaRepository<SessionGuide, UUID> {
    Optional<SessionGuide> findFirstByProjectIdOrderByVersionNumberDesc(UUID projectId);
    Optional<SessionGuide> findByProjectIdAndGeneratorContentHash(UUID projectId, String hash);
    Optional<SessionGuide> findByIdAndProjectId(UUID id, UUID projectId);
}
