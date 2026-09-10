package gal.subtitula.api.transparency.recording;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import gal.subtitula.api.transparency.model.RecordingUploadState;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingRepository extends JpaRepository<Recording, UUID> {
    List<Recording> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<Recording> findByIdAndProjectId(UUID id, UUID projectId);
    List<Recording> findByUploadStateInOrderByCreatedAtAsc(
        Collection<RecordingUploadState> states,
        Pageable pageable);
}
