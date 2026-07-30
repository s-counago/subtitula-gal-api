package gal.subtitula.api.transparency.ingest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import gal.subtitula.api.transparency.model.UploadIntentState;

public interface UploadIntentRepository extends JpaRepository<UploadIntent, UUID> {
    Optional<UploadIntent> findByProjectIdAndClientRequestId(UUID projectId, UUID clientRequestId);
    Optional<UploadIntent> findByProcessingJobId(UUID processingJobId);
    List<UploadIntent> findByStateAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
        UploadIntentState state,
        Instant expiresAt,
        Pageable pageable);
}
