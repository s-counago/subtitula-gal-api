package gal.subtitula.api.transparency.ingest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderWebhookDeliveryRepository
        extends JpaRepository<ProviderWebhookDelivery, UUID> {
    Optional<ProviderWebhookDelivery> findByProviderRequestId(String providerRequestId);
}
