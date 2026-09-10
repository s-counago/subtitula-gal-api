package gal.subtitula.api.transparency.review;

import gal.subtitula.api.transparency.model.ReviewIssueState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssue, UUID> {
    List<ReviewIssue> findByProjectIdAndStateOrderByCreatedAtAsc(
        UUID projectId,
        ReviewIssueState state);

    Optional<ReviewIssue> findByIdAndProjectId(UUID id, UUID projectId);

    List<ReviewIssue> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    long countByProjectIdAndStateAndSeverity(
        UUID projectId,
        ReviewIssueState state,
        gal.subtitula.api.transparency.model.ReviewIssueSeverity severity);
}
