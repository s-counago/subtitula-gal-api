package gal.subtitula.api.transparency.search;

import gal.subtitula.api.transparency.agenda.AgendaItem;
import gal.subtitula.api.transparency.agenda.AgendaItemRepository;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.guide.GuideContributionRepository;
import gal.subtitula.api.transparency.guide.GuideDecisionRepository;
import gal.subtitula.api.transparency.guide.GuideEvidenceLink;
import gal.subtitula.api.transparency.guide.GuideEvidenceLinkRepository;
import gal.subtitula.api.transparency.guide.GuideTopicRepository;
import gal.subtitula.api.transparency.lifecycle.ProcessingErrorCode;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobState;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import gal.subtitula.api.transparency.model.DecisionStatus;
import gal.subtitula.api.transparency.model.GuideEvidenceSubjectType;
import gal.subtitula.api.transparency.model.ProcessingEventStatus;
import gal.subtitula.api.transparency.model.PublicationState;
import gal.subtitula.api.transparency.internal.dto.LexicalIndexResponse;
import gal.subtitula.api.transparency.processing.ProcessingEvent;
import gal.subtitula.api.transparency.processing.ProcessingEventRepository;
import gal.subtitula.api.transparency.processing.ProcessingJobRepository;
import gal.subtitula.api.transparency.publication.ProjectDocument;
import gal.subtitula.api.transparency.publication.ProjectDocumentRepository;
import gal.subtitula.api.transparency.publication.Publication;
import gal.subtitula.api.transparency.publication.PublicationDocument;
import gal.subtitula.api.transparency.publication.PublicationDocumentRepository;
import gal.subtitula.api.transparency.publication.PublicationRepository;
import gal.subtitula.api.transparency.transcript.EvidenceSegment;
import gal.subtitula.api.transparency.transcript.EvidenceSegmentRepository;
import gal.subtitula.api.transparency.transcript.Speaker;
import gal.subtitula.api.transparency.transcript.SpeakerRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SearchIndexService {

    public static final String INDEX_VERSION = "lexical-v1";

    private final PublicationRepository publications;
    private final EvidenceSegmentRepository segments;
    private final SpeakerRepository speakers;
    private final AgendaItemRepository agendaItems;
    private final GuideTopicRepository topics;
    private final GuideContributionRepository contributions;
    private final GuideDecisionRepository decisions;
    private final GuideEvidenceLinkRepository evidenceLinks;
    private final PublicationDocumentRepository publicationDocuments;
    private final ProjectDocumentRepository documents;
    private final ProcessingJobRepository jobs;
    private final ProcessingEventRepository events;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransparencyCapabilities capabilities;
    private final String embeddingModel;

    public SearchIndexService(
            PublicationRepository publications,
            EvidenceSegmentRepository segments,
            SpeakerRepository speakers,
            AgendaItemRepository agendaItems,
            GuideTopicRepository topics,
            GuideContributionRepository contributions,
            GuideDecisionRepository decisions,
            GuideEvidenceLinkRepository evidenceLinks,
            PublicationDocumentRepository publicationDocuments,
            ProjectDocumentRepository documents,
            ProcessingJobRepository jobs,
            ProcessingEventRepository events,
            NamedParameterJdbcTemplate jdbc,
            TransparencyCapabilities capabilities,
            @Value("${app.search.embedding-model:@cf/baai/bge-m3}")
            String embeddingModel) {
        this.publications = publications;
        this.segments = segments;
        this.speakers = speakers;
        this.agendaItems = agendaItems;
        this.topics = topics;
        this.contributions = contributions;
        this.decisions = decisions;
        this.evidenceLinks = evidenceLinks;
        this.publicationDocuments = publicationDocuments;
        this.documents = documents;
        this.jobs = jobs;
        this.events = events;
        this.jdbc = jdbc;
        this.capabilities = capabilities;
        this.embeddingModel = embeddingModel;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LexicalIndexResponse prepare(
            UUID publicationId,
            UUID jobId,
            UUID expectedProjectId,
            String workflowInstanceId) {
        Publication publication = activePublication(publicationId);
        if (!publication.getProjectId().equals(expectedProjectId)) {
            throw new IllegalStateException("Publication does not belong to project");
        }
        var job = indexJob(publication, jobId);
        if (workflowInstanceId == null
                || workflowInstanceId.isBlank()
                || workflowInstanceId.length() > 255) {
            throw new IllegalStateException("Index Workflow ID is invalid");
        }
        job.assignWorkflow(workflowInstanceId);
        jobs.flush();
        return new LexicalIndexResponse(
            publication.getProjectId(),
            capabilities.hybridSearch());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LexicalIndexResponse index(
            UUID publicationId,
            UUID jobId,
            UUID expectedProjectId,
            String workflowInstanceId) {
        Publication publication = activePublication(publicationId);
        if (!publication.getProjectId().equals(expectedProjectId)) {
            throw new IllegalStateException("Publication does not belong to project");
        }
        var job = indexJob(publication, jobId);
        if (workflowInstanceId == null
                || !workflowInstanceId.equals(job.getWorkflowInstanceId())) {
            throw new IllegalStateException("Index Workflow is not active");
        }
        if (job.getState() == ProcessingJobState.RUNNING
                && job.getCurrentStage() == ProcessingStage.GENERATING_EMBEDDINGS) {
            return new LexicalIndexResponse(publication.getProjectId(), true);
        }
        if (job.getState() == ProcessingJobState.SUCCEEDED) {
            return new LexicalIndexResponse(publication.getProjectId(), false);
        }
        job.start(ProcessingStage.BUILDING_SEARCH_DOCUMENTS);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.BUILDING_SEARCH_DOCUMENTS,
            ProcessingEventStatus.STARTED,
            null,
            null,
            workflowInstanceId));

        rebuild(publication, jobId, job);
        return new LexicalIndexResponse(
            publication.getProjectId(),
            capabilities.hybridSearch());
    }

    private void rebuild(Publication publication, UUID jobId, gal.subtitula.api.transparency.processing.ProcessingJob job) {
        UUID publicationId = publication.getId();

        jdbc.update("""
            update search_documents
            set active = false, tombstoned_at = now()
            where project_id = :projectId and active = true
            """, Map.of("projectId", publication.getProjectId()));

        Map<UUID, Speaker> speakerMap = speakers
            .findByProjectIdOrderByProviderLabelAsc(publication.getProjectId())
            .stream()
            .collect(Collectors.toMap(Speaker::getId, Function.identity()));
        Map<UUID, AgendaItem> agendaMap = agendaItems
            .findByProjectIdOrderByOrdinalAsc(publication.getProjectId())
            .stream()
            .collect(Collectors.toMap(AgendaItem::getId, Function.identity()));
        Map<UUID, EvidenceSegment> segmentMap = segments
            .findByTranscriptRevisionIdOrderBySequenceAsc(
                publication.getTranscriptRevisionId())
            .stream()
            .collect(Collectors.toMap(EvidenceSegment::getId, Function.identity()));

        for (EvidenceSegment segment : segmentMap.values().stream()
                .sorted(java.util.Comparator.comparingInt(EvidenceSegment::getSequence))
                .toList()) {
            Speaker speaker = speakerMap.get(segment.getSpeakerId());
            insert(
                publication,
                "EVIDENCE",
                segment.getId(),
                segment,
                speaker == null ? "Persoa non identificada" : speaker.getDisplayLabel(),
                segment.getReviewedText(),
                speaker,
                null,
                null);
        }

        if (publication.getGuideId() != null) {
            var guideTopics = topics.findByGuideIdOrderByOrdinalAsc(publication.getGuideId());
            List<UUID> topicIds = guideTopics.stream().map(value -> value.getId()).toList();
            Map<UUID, List<GuideEvidenceLink>> topicLinks =
                evidence(publication.getGuideId(), GuideEvidenceSubjectType.TOPIC);
            Map<UUID, List<GuideEvidenceLink>> contributionLinks =
                evidence(publication.getGuideId(), GuideEvidenceSubjectType.CONTRIBUTION);
            Map<UUID, List<GuideEvidenceLink>> decisionLinks =
                evidence(publication.getGuideId(), GuideEvidenceSubjectType.DECISION);
            for (var topic : guideTopics) {
                EvidenceSegment evidence = firstEvidence(topicLinks.get(topic.getId()), segmentMap);
                AgendaItem agenda = agendaMap.get(topic.getAgendaItemId());
                insert(
                    publication,
                    "TOPIC",
                    topic.getId(),
                    evidence,
                    topic.getTitle(),
                    topic.getNeutralSummary() + " " + aliases(topic.getAliases()),
                    null,
                    agenda,
                    null);
            }
            if (!topicIds.isEmpty()) {
                for (var contribution :
                        contributions.findByTopicIdInOrderByOrdinalAsc(topicIds)) {
                    EvidenceSegment evidence = firstEvidence(
                        contributionLinks.get(contribution.getId()), segmentMap);
                    Speaker speaker = speakerMap.get(contribution.getSpeakerId());
                    insert(
                        publication,
                        "CONTRIBUTION",
                        contribution.getId(),
                        evidence,
                        speaker == null
                            ? "Persoa non identificada"
                            : speaker.getDisplayLabel(),
                        contribution.getNeutralSummary(),
                        speaker,
                        null,
                        null);
                }
                for (var decision : decisions.findByTopicIdInOrderByOrdinalAsc(topicIds)) {
                    if (decision.getStatus() != DecisionStatus.CONFIRMED
                            && decision.getStatus() != DecisionStatus.DOCUMENT_SUPPORTED) {
                        continue;
                    }
                    EvidenceSegment evidence = firstEvidence(
                        decisionLinks.get(decision.getId()), segmentMap);
                    AgendaItem agenda = agendaMap.get(decision.getAgendaItemId());
                    insert(
                        publication,
                        "DECISION",
                        decision.getId(),
                        evidence,
                        "Acordo confirmado",
                        decision.getNeutralDescription() + " "
                            + nullToEmpty(decision.getMotion()) + " "
                            + nullToEmpty(decision.getResult()),
                        null,
                        agenda,
                        null);
                }
            }
        }

        List<UUID> documentIds = publicationDocuments
            .findByPublicationIdOrderByOrdinalAsc(publicationId)
            .stream()
            .map(PublicationDocument::getDocumentId)
            .toList();
        Map<UUID, ProjectDocument> documentMap = documentIds.isEmpty()
            ? Map.of()
            : documents.findAllById(documentIds).stream()
                .collect(Collectors.toMap(ProjectDocument::getId, Function.identity()));
        for (UUID documentId : documentIds) {
            ProjectDocument document = documentMap.get(documentId);
            if (document == null || document.getOfficialUrl() == null) {
                continue;
            }
            insert(
                publication,
                "DOCUMENT_CHUNK",
                document.getId(),
                null,
                document.getTitle(),
                nullToEmpty(document.getIssuingBody()) + " "
                    + (document.getDocumentDate() == null
                        ? ""
                        : document.getDocumentDate()),
                null,
                null,
                document.getOfficialUrl());
        }

        job.advance(ProcessingStage.PERSISTING_INDEX);
        job.configure(
            null,
            null,
            capabilities.hybridSearch() ? embeddingModel : null,
            null,
            INDEX_VERSION);
        events.save(ProcessingEvent.create(
            jobId,
            ProcessingStage.PERSISTING_INDEX,
            ProcessingEventStatus.SUCCEEDED,
            null,
            null,
            "publication:" + publicationId));
        if (capabilities.hybridSearch()) {
            job.advance(ProcessingStage.GENERATING_EMBEDDINGS);
            events.save(ProcessingEvent.create(
                jobId,
                ProcessingStage.GENERATING_EMBEDDINGS,
                ProcessingEventStatus.WAITING,
                null,
                null,
                "publication:" + publicationId));
        } else {
            job.succeed(hash(publicationId + ":" + INDEX_VERSION), null);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID jobId) {
        jobs.findById(jobId).ifPresent(job -> {
            if (job.getType() != ProcessingJobType.INDEX
                    && job.getType() != ProcessingJobType.REINDEX) {
                throw new IllegalStateException("Job is not a search index job");
            }
            job.fail(ProcessingErrorCode.INDEX_FAILED, "A busca desta sesión aínda non está lista.");
            events.save(ProcessingEvent.create(
                jobId,
                job.getCurrentStage(),
                ProcessingEventStatus.FAILED,
                null,
                null,
                "index-failure:" + jobId));
        });
    }

    private Publication activePublication(UUID publicationId) {
        return publications.findById(publicationId)
            .filter(value -> value.getState() == PublicationState.PUBLISHED)
            .orElseThrow(() -> new IllegalStateException("Publication is not active"));
    }

    private gal.subtitula.api.transparency.processing.ProcessingJob indexJob(
            Publication publication,
            UUID jobId) {
        var job = jobs.findById(jobId)
            .orElseThrow(() -> new IllegalStateException("Index job not found"));
        String expectedKey = "index:" + publication.getId() + ":" + INDEX_VERSION;
        String expectedReindexPrefix = "reindex:" + publication.getId()
            + ":" + INDEX_VERSION + ":";
        if (!publication.getProjectId().equals(job.getProjectId())
                || (!expectedKey.equals(job.getIdempotencyKey())
                    && !job.getIdempotencyKey().startsWith(expectedReindexPrefix))) {
            throw new IllegalStateException("Index job does not belong to publication");
        }
        return job;
    }

    private void insert(
            Publication publication,
            String kind,
            UUID sourceId,
            EvidenceSegment evidence,
            String title,
            String text,
            Speaker speaker,
            AgendaItem agenda,
            String documentUrl) {
        String displayText = (text == null || text.isBlank()) ? title : text.trim();
        String searchable = String.join(" ",
            title,
            displayText,
            speaker == null ? "" : speaker.getDisplayLabel(),
            speaker == null ? "" : nullToEmpty(speaker.getRole()),
            agenda == null ? "" : agenda.getTitle(),
            publication.getTitle(),
            nullToEmpty(publication.getSessionBody()));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("publicationId", publication.getId())
            .addValue("projectId", publication.getProjectId())
            .addValue("kind", kind)
            .addValue("sourceId", sourceId)
            .addValue("evidenceId", evidence == null ? null : evidence.getId())
            .addValue("title", title)
            .addValue("text", displayText)
            .addValue("searchable", searchable)
            .addValue("slug", publication.getPublicSlug())
            .addValue("publicationVersion", publication.getVersionNumber())
            .addValue("sessionTitle", publication.getTitle())
            .addValue("organizationId", publication.getOrganizationId())
            .addValue("sessionBody", publication.getSessionBody())
            .addValue("sessionDate", publication.getSessionDate())
            .addValue("language", publication.getLanguageCode())
            .addValue("speakerId", speaker == null ? null : speaker.getId())
            .addValue("speakerLabel", speaker == null ? null : speaker.getDisplayLabel())
            .addValue("agendaId", agenda == null ? null : agenda.getId())
            .addValue("agendaTitle", agenda == null ? null : agenda.getTitle())
            .addValue("documentUrl", documentUrl)
            .addValue("startMs", evidence == null ? null : evidence.getStartMs())
            .addValue("endMs", evidence == null ? null : evidence.getEndMs())
            .addValue("hash", hash(kind + "\u001f" + sourceId + "\u001f" + searchable))
            .addValue("indexVersion", INDEX_VERSION);
        jdbc.update("""
            insert into search_documents (
                id, publication_id, project_id, document_kind, source_entity_id,
                evidence_segment_id, display_title, display_text, normalized_text,
                search_vector, public_slug, publication_version, session_title,
                organization_id, session_body, session_date, language_code,
                speaker_id, speaker_label, agenda_item_id, agenda_title,
                document_url, start_ms, end_ms, content_hash, index_version
            ) values (
                :id, :publicationId, :projectId, :kind, :sourceId,
                :evidenceId, :title, :text, lower(unaccent(:searchable)),
                setweight(to_tsvector('simple', unaccent(:title)), 'A')
                  || setweight(to_tsvector('simple', unaccent(
                    coalesce(:speakerLabel, '') || ' ' || coalesce(:agendaTitle, '')
                  )), 'B')
                  || setweight(to_tsvector('simple', unaccent(:text)), 'C')
                  || setweight(to_tsvector('simple', unaccent(
                    :sessionTitle || ' ' || coalesce(:sessionBody, '')
                  )), 'D'),
                :slug, :publicationVersion, :sessionTitle,
                :organizationId, :sessionBody, :sessionDate, :language,
                :speakerId, :speakerLabel, :agendaId, :agendaTitle,
                :documentUrl, :startMs, :endMs, :hash, :indexVersion
            )
            on conflict (publication_id, document_kind, source_entity_id)
            do update set
                evidence_segment_id = excluded.evidence_segment_id,
                display_title = excluded.display_title,
                display_text = excluded.display_text,
                normalized_text = excluded.normalized_text,
                search_vector = excluded.search_vector,
                public_slug = excluded.public_slug,
                publication_version = excluded.publication_version,
                session_title = excluded.session_title,
                organization_id = excluded.organization_id,
                session_body = excluded.session_body,
                session_date = excluded.session_date,
                language_code = excluded.language_code,
                speaker_id = excluded.speaker_id,
                speaker_label = excluded.speaker_label,
                agenda_item_id = excluded.agenda_item_id,
                agenda_title = excluded.agenda_title,
                document_url = excluded.document_url,
                start_ms = excluded.start_ms,
                end_ms = excluded.end_ms,
                content_hash = excluded.content_hash,
                index_version = excluded.index_version,
                embedding = null,
                embedding_model = null,
                embedded_content_hash = null,
                embedded_at = null,
                active = true,
                tombstoned_at = null
            """, parameters);
    }

    private Map<UUID, List<GuideEvidenceLink>> evidence(
            UUID guideId,
            GuideEvidenceSubjectType type) {
        return evidenceLinks
            .findByGuideIdOrderBySubjectTypeAscSubjectIdAscOrdinalAsc(guideId)
            .stream()
            .filter(value -> value.getSubjectType() == type)
            .collect(Collectors.groupingBy(GuideEvidenceLink::getSubjectId));
    }

    private static EvidenceSegment firstEvidence(
            List<GuideEvidenceLink> links,
            Map<UUID, EvidenceSegment> segments) {
        if (links == null || links.isEmpty()) {
            throw new IllegalStateException("Search source has no evidence");
        }
        EvidenceSegment segment = segments.get(links.get(0).getEvidenceSegmentId());
        if (segment == null) {
            throw new IllegalStateException("Search evidence is invalid");
        }
        return segment;
    }

    private static String aliases(com.fasterxml.jackson.databind.JsonNode aliases) {
        if (aliases == null || !aliases.isArray()) {
            return "";
        }
        StringBuilder value = new StringBuilder();
        aliases.forEach(alias -> {
            if (alias.isTextual()) {
                value.append(alias.asText()).append(' ');
            }
        });
        return value.toString();
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
