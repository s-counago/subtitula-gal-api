package gal.subtitula.api.font;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FontRepository extends JpaRepository<Font, UUID> {
    List<Font> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Font> findByIdAndUserId(UUID id, UUID userId);
}
