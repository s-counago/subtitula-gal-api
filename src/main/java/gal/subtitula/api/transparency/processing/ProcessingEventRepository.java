package gal.subtitula.api.transparency.processing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessingEventRepository extends JpaRepository<ProcessingEvent, UUID> {
}
