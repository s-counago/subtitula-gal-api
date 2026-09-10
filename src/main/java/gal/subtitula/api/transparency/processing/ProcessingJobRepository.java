package gal.subtitula.api.transparency.processing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobState;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {
    Optional<ProcessingJob> findFirstByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<ProcessingJob> findFirstByProjectIdAndTypeInOrderByCreatedAtDesc(
        UUID projectId, List<ProcessingJobType> types);
    List<ProcessingJob> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    Optional<ProcessingJob> findByIdAndProjectId(UUID id, UUID projectId);
    Optional<ProcessingJob> findByIdempotencyKey(String idempotencyKey);
    Optional<ProcessingJob> findByProviderRequestId(String providerRequestId);
    List<ProcessingJob> findByStateInOrderByCreatedAtAsc(
        List<ProcessingJobState> states,
        Pageable pageable);
}
