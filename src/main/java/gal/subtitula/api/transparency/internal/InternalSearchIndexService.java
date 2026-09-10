package gal.subtitula.api.transparency.internal;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectRepository;
import gal.subtitula.api.transparency.TransparencyStateConflictException;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.internal.dto.EmbeddingBatchCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingCompleteCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingFailureCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingIndexContextResponse;
import gal.subtitula.api.transparency.internal.dto.EmbeddingStartCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingStartResponse;
import gal.subtitula.api.transparency.lifecycle.ProcessingErrorCode;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobState;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import gal.subtitula.api.transparency.model.ProcessingEventStatus;
import gal.subtitula.api.transparency.model.PublicationState;
import gal.subtitula.api.transparency.processing.ProcessingEvent;
import gal.subtitula.api.transparency.processing.ProcessingEventRepository;
import gal.subtitula.api.transparency.processing.ProcessingJob;
import gal.subtitula.api.transparency.processing.ProcessingJobRepository;
import gal.subtitula.api.transparency.publication.Publication;
import gal.subtitula.api.transparency.publication.PublicationRepository;
import gal.subtitula.api.transparency.search.SearchIndexService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class InternalSearchIndexService {

    private final ProcessingJobRepository jobs;
    private final ProcessingEventRepository events;
    private final ProjectRepository projects;
    private final PublicationRepository publications;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransparencyCapabilities capabilities;
    private final String modelVersion;
    private final int dimensions;

    public InternalSearchIndexService(
            ProcessingJobRepository jobs,
            ProcessingEventRepository events,
            ProjectRepository projects,
            PublicationRepository publications,
            NamedParameterJdbcTemplate jdbc,
            TransparencyCapabilities capabilities,
            @Value("${app.search.embedding-model:@cf/baai/bge-m3}")
            String modelVersion,
            @Value("${app.search.embedding-dimensions:1024}")
            int dimensions) {
        this.jobs = jobs;
        this.events = events;
        this.projects = projects;
        this.publications = publications;
        this.jdbc = jdbc;
        this.capabilities = capabilities;
        this.modelVersion = modelVersion;
        this.dimensions = dimensions;
    }

    @Transactional(readOnly = true)
    public EmbeddingIndexContextResponse context(UUID jobId, UUID publicationId) {
        requireCapability();
        Aggregate aggregate = aggregate(jobId, publicationId);
        List<EmbeddingIndexContextResponse.Document> documents = jdbc.query("""
            select
                id,
                content_hash,
                concat_ws(E'\\n',
                    display_title,
                    display_text,
                    speaker_label,
                    agenda_title,
                    session_title,
                    session_body
                ) as embedding_text
            from search_documents
            where publication_id = :publicationId and active = true
            order by document_kind, start_ms nulls last, id
            """, new MapSqlParameterSource("publicationId", publicationId),
            (result, row) -> new EmbeddingIndexContextResponse.Document(
                result.getObject("id", UUID.class),
                result.getString("content_hash"),
                result.getString("embedding_text")));
        if (documents.isEmpty() || documents.size() > 50_000) {
            throw conflict("Embedding context has an invalid document count");
        }
        return new EmbeddingIndexContextResponse(
            aggregate.job().getId(),
            aggregate.project().getId(),
            aggregate.publication().getId(),
            modelVersion,
            dimensions,
            aggregate.job().getVersion(),
            aggregate.project().getVersion(),
            documents);
    }

    @Transactional
    public EmbeddingStartResponse start(
            UUID jobId,
            UUID publicationId,
            EmbeddingStartCommand command) {
        requireCapability();
        Aggregate aggregate = aggregate(jobId, publicationId);
        ProcessingJob job = aggregate.job();
        Project project = aggregate.project();
        String workflow = boundedRequired(command.workflowInstanceId(), 255);
        if (workflow.equals(job.getWorkflowInstanceId())
                && job.getState() == ProcessingJobState.RUNNING
                && job.getCurrentStage() == ProcessingStage.GENERATING_EMBEDDINGS) {
            return response(aggregate);
        }
        checkVersion(job.getVersion(), command.expectedJobVersion(), "processing job");
        checkVersion(project.getVersion(), command.expectedProjectVersion(), "project");
        if (job.getState() == ProcessingJobState.FAILED_RETRYABLE) {
            job.assignWorkflow(workflow);
            job.start(ProcessingStage.GENERATING_EMBEDDINGS);
        } else if (job.getState() == ProcessingJobState.RUNNING
                && job.getCurrentStage() == ProcessingStage.GENERATING_EMBEDDINGS
                && job.getWorkflowInstanceId() == null) {
            job.assignWorkflow(workflow);
        } else {
            throw conflict("Embedding job is not waiting for a Workflow");
        }
        job.configure(
            job.getInputHash(),
            job.getInputArtifactKey(),
            modelVersion,
            job.getPromptVersion(),
            SearchIndexService.INDEX_VERSION);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.GENERATING_EMBEDDINGS,
            ProcessingEventStatus.STARTED,
            null,
            null,
            workflow));
        jobs.flush();
        events.flush();
        return response(new Aggregate(job, project, aggregate.publication()));
    }

    @Transactional
    public void ingestBatch(
            UUID jobId,
            UUID publicationId,
            EmbeddingBatchCommand command) {
        requireCapability();
        Aggregate aggregate = aggregate(jobId, publicationId);
        requireRunningWorkflow(aggregate.job(), command.workflowInstanceId());
        requireModel(command.modelVersion(), command.dimensions());
        if (command.items() == null
                || command.items().isEmpty()
                || command.items().size() > 64) {
            throw conflict("Embedding batch size is invalid");
        }
        Set<UUID> seen = new HashSet<>();
        for (EmbeddingBatchCommand.Item item : command.items()) {
            if (item == null || item.searchDocumentId() == null
                    || !seen.add(item.searchDocumentId())
                    || item.contentHash() == null
                    || !item.contentHash().matches("^[a-f0-9]{64}$")
                    || item.embedding() == null
                    || item.embedding().size() != dimensions) {
                throw conflict("Embedding item is invalid");
            }
            String vector = vector(item.embedding());
            int updated = jdbc.update("""
                update search_documents
                set embedding = cast(:embedding as vector),
                    embedding_model = :model,
                    embedded_content_hash = content_hash,
                    embedded_at = now()
                where id = :id
                  and publication_id = :publicationId
                  and active = true
                  and content_hash = :contentHash
                """, new MapSqlParameterSource()
                    .addValue("embedding", vector)
                    .addValue("model", modelVersion)
                    .addValue("id", item.searchDocumentId())
                    .addValue("publicationId", publicationId)
                    .addValue("contentHash", item.contentHash()));
            if (updated != 1) {
                throw conflict("Embedding item does not match the active index");
            }
        }
    }

    @Transactional
    public void complete(
            UUID jobId,
            UUID publicationId,
            EmbeddingCompleteCommand command) {
        requireCapability();
        Aggregate aggregate = aggregate(jobId, publicationId);
        ProcessingJob job = aggregate.job();
        requireRunningWorkflow(job, command.workflowInstanceId());
        requireModel(command.modelVersion(), dimensions);
        Integer missing = jdbc.queryForObject("""
            select count(*)
            from search_documents
            where publication_id = :publicationId
              and active = true
              and (
                    embedding is null
                 or embedding_model <> :model
                 or embedded_content_hash <> content_hash
              )
            """, new MapSqlParameterSource()
                .addValue("publicationId", publicationId)
                .addValue("model", modelVersion), Integer.class);
        if (missing == null || missing != 0) {
            throw conflict("Embedding projection is incomplete");
        }
        Integer count = jdbc.queryForObject("""
            select count(*) from search_documents
            where publication_id = :publicationId and active = true
            """, new MapSqlParameterSource("publicationId", publicationId), Integer.class);
        if (count == null || count == 0) {
            throw conflict("Embedding projection is empty");
        }
        job.advance(ProcessingStage.PERSISTING_INDEX);
        job.configure(
            job.getInputHash(),
            job.getInputArtifactKey(),
            modelVersion,
            job.getPromptVersion(),
            SearchIndexService.INDEX_VERSION);
        recordUsage(
            job,
            command.providerUsage(),
            command.costMicrounits(),
            command.costCurrency());
        job.succeed(
            hash(publicationId + ":" + modelVersion + ":" + count),
            null);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.PERSISTING_INDEX,
            ProcessingEventStatus.SUCCEEDED,
            null,
            command.providerUsage(),
            command.workflowInstanceId()));
    }

    @Transactional
    public void fail(
            UUID jobId,
            UUID publicationId,
            EmbeddingFailureCommand command) {
        requireCapability();
        Aggregate aggregate = aggregate(jobId, publicationId);
        ProcessingJob job = aggregate.job();
        if (job.getState() == ProcessingJobState.SUCCEEDED
                || job.getState() == ProcessingJobState.FAILED_TERMINAL) {
            return;
        }
        if (job.getWorkflowInstanceId() != null
                && !job.getWorkflowInstanceId().equals(command.workflowInstanceId())) {
            throw conflict("Embedding failure belongs to another Workflow");
        }
        ProcessingErrorCode code = "index_failed".equalsIgnoreCase(command.errorCode())
            ? ProcessingErrorCode.INDEX_FAILED
            : ProcessingErrorCode.EMBEDDING_UNAVAILABLE;
        job.fail(code, bounded(command.safeMessage(), 500));
        events.save(ProcessingEvent.create(
            jobId,
            job.getCurrentStage(),
            ProcessingEventStatus.FAILED,
            null,
            null,
            command.workflowInstanceId()));
    }

    private Aggregate aggregate(UUID jobId, UUID publicationId) {
        ProcessingJob job = jobs.findById(jobId)
            .orElseThrow(() -> conflict("Embedding job not found"));
        Project project = projects.findById(job.getProjectId())
            .orElseThrow(() -> conflict("Embedding project not found"));
        Publication publication = publications.findById(publicationId)
            .filter(value -> value.getProjectId().equals(project.getId()))
            .filter(value -> value.getState() == PublicationState.PUBLISHED)
            .orElseThrow(() -> conflict("Embedding publication is not active"));
        if (job.getType() != ProcessingJobType.INDEX
                && job.getType() != ProcessingJobType.REINDEX) {
            throw conflict("Job is not a search index job");
        }
        String expectedKey = "index:" + publicationId
            + ":" + SearchIndexService.INDEX_VERSION;
        String expectedReindexPrefix = "reindex:" + publicationId
            + ":" + SearchIndexService.INDEX_VERSION + ":";
        if (!expectedKey.equals(job.getIdempotencyKey())
                && !(job.getType() == ProcessingJobType.REINDEX
                    && job.getIdempotencyKey().startsWith(expectedReindexPrefix))) {
            throw conflict("Embedding job does not belong to publication");
        }
        return new Aggregate(job, project, publication);
    }

    private void requireRunningWorkflow(ProcessingJob job, String workflow) {
        if (job.getState() != ProcessingJobState.RUNNING
                || job.getCurrentStage() != ProcessingStage.GENERATING_EMBEDDINGS
                || workflow == null
                || !workflow.equals(job.getWorkflowInstanceId())) {
            throw conflict("Embedding Workflow is not active");
        }
    }

    private void requireModel(String requestedModel, int requestedDimensions) {
        if (!modelVersion.equals(requestedModel) || requestedDimensions != dimensions) {
            throw conflict("Embedding model contract does not match");
        }
    }

    private void requireCapability() {
        if (!capabilities.hybridSearch()) {
            throw conflict("Hybrid search is not enabled");
        }
    }

    private EmbeddingStartResponse response(Aggregate aggregate) {
        return new EmbeddingStartResponse(
            aggregate.job().getId(),
            aggregate.project().getId(),
            aggregate.publication().getId(),
            aggregate.job().getState().name().toLowerCase(Locale.ROOT),
            aggregate.job().getCurrentStage().name().toLowerCase(Locale.ROOT),
            aggregate.job().getVersion(),
            aggregate.project().getVersion());
    }

    private static String vector(List<Double> values) {
        List<String> normalized = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || Math.abs(value) > 1_000) {
                throw conflict("Embedding contains a non-finite or out-of-range value");
            }
            normalized.add(Double.toString(value));
        }
        return "[" + String.join(",", normalized) + "]";
    }

    private static void recordUsage(
            ProcessingJob job,
            com.fasterxml.jackson.databind.JsonNode usage,
            Long costMicrounits,
            String costCurrency) {
        if (usage == null && costMicrounits == null && costCurrency == null) {
            return;
        }
        if (usage != null && (!usage.isObject() || usage.toString().length() > 20_000)) {
            throw conflict("Provider usage payload is invalid");
        }
        if (costMicrounits == null
                || costMicrounits < 0
                || costMicrounits > 1_000_000_000_000L
                || costCurrency == null
                || !costCurrency.matches("^[A-Z]{3}$")) {
            throw conflict("Provider cost payload is invalid");
        }
        job.recordUsage(usage, costMicrounits, costCurrency);
    }

    private static String boundedRequired(String value, int max) {
        String normalized = bounded(value, max);
        if (normalized == null) {
            throw conflict("Embedding value is required");
        }
        return normalized;
    }

    private static String bounded(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > max) throw conflict("Embedding value is too long");
        return normalized;
    }

    private static void checkVersion(long actual, long expected, String resource) {
        if (actual != expected) throw conflict("Stale " + resource);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static TransparencyStateConflictException conflict(String message) {
        return new TransparencyStateConflictException(message);
    }

    private record Aggregate(
            ProcessingJob job,
            Project project,
            Publication publication) {
    }
}
