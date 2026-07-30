package gal.subtitula.api.transparency.processing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {
    Optional<ProcessingJob> findFirstByProjectIdOrderByCreatedAtDesc(UUID projectId);
    List<ProcessingJob> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    Optional<ProcessingJob> findByIdAndProjectId(UUID id, UUID projectId);
    Optional<ProcessingJob> findByIdempotencyKey(String idempotencyKey);
    Optional<ProcessingJob> findByProviderRequestId(String providerRequestId);
}
