package gal.subtitula.api.transparency.transcript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {
    List<Speaker> findByProjectIdOrderByProviderLabelAsc(UUID projectId);
    Optional<Speaker> findByIdAndProjectId(UUID id, UUID projectId);
}
