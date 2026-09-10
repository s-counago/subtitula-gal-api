package gal.subtitula.api.transparency.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession, UUID> {
    List<ReviewSession> findByProjectIdOrderByStartedAtAsc(UUID projectId);

    Optional<ReviewSession> findByProjectIdAndReviewerIdAndCompletedAtIsNull(
        UUID projectId,
        UUID reviewerId);

    Optional<ReviewSession> findByIdAndProjectIdAndReviewerId(
        UUID id,
        UUID projectId,
        UUID reviewerId);
}
