package gal.subtitula.api.transparency.guide;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GuideTopicRepository extends JpaRepository<GuideTopic, UUID> {
    List<GuideTopic> findByGuideIdOrderByOrdinalAsc(UUID guideId);
}
