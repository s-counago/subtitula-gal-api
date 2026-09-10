package gal.subtitula.api.transparency.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import gal.subtitula.api.transparency.model.ProcessingEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processing_events")
public class ProcessingEvent {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingEventStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_usage", nullable = false, columnDefinition = "jsonb")
    private JsonNode providerUsage;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProcessingEvent() {
    }

    public static ProcessingEvent create(
            UUID jobId,
            ProcessingStage stage,
            ProcessingEventStatus status,
            Long durationMs,
            JsonNode providerUsage,
            String correlationId) {
        var event = new ProcessingEvent();
        event.id = UUID.randomUUID();
        event.jobId = jobId;
        event.stage = stage;
        event.status = status;
        event.durationMs = durationMs;
        event.providerUsage = providerUsage == null
            ? JsonNodeFactory.instance.objectNode()
            : providerUsage;
        event.correlationId = correlationId;
        event.createdAt = Instant.now();
        return event;
    }

    public UUID getId() {
        return id;
    }
}
