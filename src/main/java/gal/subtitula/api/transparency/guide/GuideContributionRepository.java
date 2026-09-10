package gal.subtitula.api.transparency.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GuideContributionRepository extends JpaRepository<GuideContribution, UUID> {
    List<GuideContribution> findByTopicIdInOrderByOrdinalAsc(Collection<UUID> topicIds);
}
