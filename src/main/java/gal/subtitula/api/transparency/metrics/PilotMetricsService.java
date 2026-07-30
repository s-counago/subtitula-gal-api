package gal.subtitula.api.transparency.metrics;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.metrics.dto.PilotMetricsResponse;
import gal.subtitula.api.transparency.metrics.dto.PilotMetricsResponse.CostTotal;
import gal.subtitula.api.transparency.metrics.dto.PilotMetricsResponse.JobMetrics;
import gal.subtitula.api.transparency.metrics.dto.PilotMetricsResponse.ProcessingMetrics;
import gal.subtitula.api.transparency.metrics.dto.PilotMetricsResponse.ReviewMetrics;
import gal.subtitula.api.transparency.metrics.dto.PilotMetricsResponse.SearchMetrics;
import gal.subtitula.api.transparency.processing.ProcessingJob;
import gal.subtitula.api.transparency.processing.ProcessingJobRepository;
import gal.subtitula.api.transparency.publication.Publication;
import gal.subtitula.api.transparency.publication.PublicationRepository;
import gal.subtitula.api.transparency.recording.RecordingRepository;
import gal.subtitula.api.transparency.review.ReviewSession;
import gal.subtitula.api.transparency.review.ReviewSessionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PilotMetricsService {

    private final ProjectService projects;
    private final ProcessingJobRepository jobs;
    private final ReviewSessionRepository reviewSessions;
    private final PublicationRepository publications;
    private final RecordingRepository recordings;
    private final NamedParameterJdbcTemplate jdbc;

    public PilotMetricsService(
            ProjectService projects,
            ProcessingJobRepository jobs,
            ReviewSessionRepository reviewSessions,
            PublicationRepository publications,
            RecordingRepository recordings,
            NamedParameterJdbcTemplate jdbc) {
        this.projects = projects;
        this.jobs = jobs;
        this.reviewSessions = reviewSessions;
        this.publications = publications;
        this.recordings = recordings;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PilotMetricsResponse get(UUID projectId, UUID userId) {
        Project project = projects.get(projectId, userId);
        List<ProcessingJob> projectJobs =
            jobs.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<ReviewSession> reviews =
            reviewSessions.findByProjectIdOrderByStartedAtAsc(projectId);
        List<Publication> snapshots =
            publications.findByProjectIdOrderByVersionNumberAsc(projectId);

        long mediaDurationMs = recordings.findByProjectIdOrderByCreatedAtDesc(projectId)
            .stream()
            .map(recording -> recording.getDurationMs())
            .filter(duration -> duration != null && duration > 0)
            .findFirst()
            .orElseGet(() -> Math.max(0, Math.round(project.getDurationSec() * 1_000)));

        List<Instant> publishedAt = snapshots.stream()
            .map(Publication::getPublishedAt)
            .filter(value -> value != null)
            .sorted()
            .toList();
        Instant firstPublishedAt = publishedAt.isEmpty() ? null : publishedAt.getFirst();
        Instant latestPublishedAt =
            publishedAt.isEmpty() ? null : publishedAt.getLast();
        Long timeToFirstPublicationMs = firstPublishedAt == null
            ? null
            : Math.max(0, Duration.between(
                project.getCreatedAt(),
                firstPublishedAt).toMillis());

        Map<String, Long> processingCost = new LinkedHashMap<>();
        List<JobMetrics> jobMetrics = new ArrayList<>();
        for (ProcessingJob job : projectJobs) {
            if (job.getCostMicrounits() != null && job.getCostCurrency() != null) {
                processingCost.merge(
                    job.getCostCurrency(),
                    job.getCostMicrounits(),
                    Math::addExact);
            }
            jobMetrics.add(new JobMetrics(
                job.getId(),
                lower(job.getType()),
                lower(job.getState()),
                job.getModelVersion(),
                job.getAttemptCount(),
                job.getCostMicrounits(),
                job.getCostCurrency(),
                job.getStartedAt(),
                job.getCompletedAt()));
        }

        ReviewMetrics review = new ReviewMetrics(
            reviews.size(),
            reviews.stream().mapToLong(ReviewSession::getActiveDurationMs).sum(),
            reviews.stream().mapToInt(ReviewSession::getResolvedCount).sum(),
            reviews.stream().mapToInt(ReviewSession::getDismissedCount).sum(),
            reviews.stream().mapToInt(ReviewSession::getManualEditCount).sum());

        ProcessingMetrics processing = new ProcessingMetrics(
            totals(processingCost),
            perMediaHour(processingCost, mediaDurationMs),
            List.copyOf(jobMetrics));

        return new PilotMetricsResponse(
            projectId,
            Instant.now(),
            mediaDurationMs,
            project.getCreatedAt(),
            firstPublishedAt,
            latestPublishedAt,
            timeToFirstPublicationMs,
            review,
            processing,
            searchMetrics(projectId));
    }

    private SearchMetrics searchMetrics(UUID projectId) {
        MapSqlParameterSource params =
            new MapSqlParameterSource("projectId", projectId);
        return jdbc.queryForObject("""
            with scoped_queries as (
                select distinct query_event.*
                from search_query_events query_event
                where exists (
                    select 1
                    from publications publication
                    where publication.project_id = :projectId
                      and query_event.filters ->> 'session' =
                          publication.public_slug
                )
                or exists (
                    select 1
                    from search_click_events click_event
                    join search_documents document
                      on document.id = click_event.search_document_id
                    join publications publication
                      on publication.id = document.publication_id
                    where click_event.query_event_id = query_event.id
                      and publication.project_id = :projectId
                )
            ),
            click_summary as (
                select count(*) as click_count
                from search_click_events click_event
                join search_documents document
                  on document.id = click_event.search_document_id
                join publications publication
                  on publication.id = document.publication_id
                where publication.project_id = :projectId
            )
            select
                count(*) as query_count,
                count(*) filter (where search_mode = 'HYBRID')
                    as hybrid_count,
                count(*) filter (where result_count = 0)
                    as no_result_count,
                coalesce((select click_count from click_summary), 0)
                    as click_count,
                percentile_cont(0.5) within group (order by latency_ms)
                    as p50_latency,
                percentile_cont(0.95) within group (order by latency_ms)
                    as p95_latency,
                coalesce(sum(estimated_ai_cost_microunits), 0)
                    as estimated_cost
            from scoped_queries
            """, params, (result, row) -> new SearchMetrics(
                result.getLong("query_count"),
                result.getLong("hybrid_count"),
                result.getLong("no_result_count"),
                result.getLong("click_count"),
                nullableRoundedLong(result.getObject("p50_latency")),
                nullableRoundedLong(result.getObject("p95_latency")),
                List.of(new CostTotal("USD", result.getLong("estimated_cost")))));
    }

    private static Long nullableRoundedLong(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        return Math.round(number.doubleValue());
    }

    private static List<CostTotal> totals(Map<String, Long> costs) {
        return costs.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new CostTotal(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static List<CostTotal> perMediaHour(
            Map<String, Long> costs,
            long mediaDurationMs) {
        if (mediaDurationMs <= 0) {
            return List.of();
        }
        return costs.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(entry -> new CostTotal(
                entry.getKey(),
                Math.round(entry.getValue() * 3_600_000.0 / mediaDurationMs)))
            .toList();
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
