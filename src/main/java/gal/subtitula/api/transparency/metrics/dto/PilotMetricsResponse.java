package gal.subtitula.api.transparency.metrics.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PilotMetricsResponse(
        UUID projectId,
        Instant measuredAt,
        long mediaDurationMs,
        Instant projectCreatedAt,
        Instant firstPublishedAt,
        Instant latestPublishedAt,
        Long timeToFirstPublicationMs,
        ReviewMetrics review,
        ProcessingMetrics processing,
        SearchMetrics publicSearch) {

    public record ReviewMetrics(
            int sessionCount,
            long activeDurationMs,
            int resolvedIssueCount,
            int dismissedIssueCount,
            int manualEditCount) {
    }

    public record ProcessingMetrics(
            List<CostTotal> configuredGrossCost,
            List<CostTotal> configuredGrossCostPerMediaHour,
            List<JobMetrics> jobs) {
    }

    public record CostTotal(
            String currency,
            long microunits) {
    }

    public record JobMetrics(
            UUID jobId,
            String type,
            String state,
            String modelVersion,
            int attempts,
            Long costMicrounits,
            String costCurrency,
            Instant startedAt,
            Instant completedAt) {
    }

    public record SearchMetrics(
            long attributedQueryCount,
            long hybridQueryCount,
            long noResultQueryCount,
            long evidenceClickCount,
            Long p50LatencyMs,
            Long p95LatencyMs,
            List<CostTotal> estimatedHybridQueryCost) {
    }
}
