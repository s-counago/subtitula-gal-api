package gal.subtitula.api.transparency.guide;

import gal.subtitula.api.transparency.model.GuideEvidenceSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GuideEvidenceLinkRepository extends JpaRepository<GuideEvidenceLink, UUID> {
    List<GuideEvidenceLink> findByGuideIdOrderBySubjectTypeAscSubjectIdAscOrdinalAsc(UUID guideId);
    long countBySubjectTypeAndSubjectId(
        GuideEvidenceSubjectType subjectType,
        UUID subjectId);
    List<GuideEvidenceLink> findBySubjectTypeAndSubjectIdIn(
        GuideEvidenceSubjectType subjectType,
        Collection<UUID> subjectIds);
}
