package gal.subtitula.api.transparency.transcript;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TranscriptEditEventRepository extends JpaRepository<TranscriptEditEvent, UUID> {
}
