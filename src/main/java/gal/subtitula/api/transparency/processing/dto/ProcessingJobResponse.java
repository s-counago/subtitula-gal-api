package gal.subtitula.api.transparency.processing.dto;

import java.time.Instant;
import java.util.UUID;

public record ProcessingJobResponse(
        UUID id,
        String type,
        String state,
        String stage,
        int attempt,
        Instant lastUpdatedAt,
        String safeErrorCode,
        String safeErrorMessage,
        boolean retryable,
        Long costMicrounits,
        String costCurrency,
        Instant startedAt,
        Instant completedAt,
        long version) {
}
