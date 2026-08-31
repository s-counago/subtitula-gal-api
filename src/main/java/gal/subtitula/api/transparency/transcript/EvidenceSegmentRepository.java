package gal.subtitula.api.transparency.transcript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceSegmentRepository extends JpaRepository<EvidenceSegment, UUID> {
    List<EvidenceSegment> findByTranscriptRevisionIdOrderBySequenceAsc(UUID transcriptRevisionId);
    Optional<EvidenceSegment> findByIdAndTranscriptRevisionId(
        UUID id,
        UUID transcriptRevisionId);
}
