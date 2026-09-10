package gal.subtitula.api.transparency.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuideDecisionRepository extends JpaRepository<GuideDecision, UUID> {
    List<GuideDecision> findByTopicIdInOrderByOrdinalAsc(Collection<UUID> topicIds);
    Optional<GuideDecision> findByIdAndTopicIdIn(UUID id, Collection<UUID> topicIds);
}
