package gal.subtitula.api.transparency.ingest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_webhook_deliveries")
public class ProviderWebhookDelivery {

    @Id
    private UUID id;

    @Column(name = "processing_job_id", nullable = false, updatable = false)
    private UUID processingJobId;

    @Column(name = "provider_request_id", nullable = false, updatable = false)
    private String providerRequestId;

    @Column(name = "payload_digest", nullable = false, updatable = false, length = 64)
    private String payloadDigest;

    @Column(name = "artifact_key", nullable = false, updatable = false, length = 1024)
    private String artifactKey;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected ProviderWebhookDelivery() {
    }

    public static ProviderWebhookDelivery create(
            UUID jobId,
            String providerRequestId,
            String payloadDigest,
            String artifactKey) {
        var delivery = new ProviderWebhookDelivery();
        delivery.id = UUID.randomUUID();
        delivery.processingJobId = jobId;
        delivery.providerRequestId = providerRequestId;
        delivery.payloadDigest = payloadDigest;
        delivery.artifactKey = artifactKey;
        delivery.receivedAt = Instant.now();
        return delivery;
    }

    public UUID getProcessingJobId() {
        return processingJobId;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public String getPayloadDigest() {
        return payloadDigest;
    }

    public String getArtifactKey() {
        return artifactKey;
    }
}
