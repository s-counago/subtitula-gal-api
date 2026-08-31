package gal.subtitula.api.transparency.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import gal.subtitula.api.project.ProjectRepository;
import gal.subtitula.api.support.AbstractIntegrationTest;
import gal.subtitula.api.transparency.agenda.AgendaItem;
import gal.subtitula.api.transparency.agenda.AgendaItemRepository;
import gal.subtitula.api.transparency.guide.GuideContribution;
import gal.subtitula.api.transparency.guide.GuideContributionRepository;
import gal.subtitula.api.transparency.guide.GuideDecision;
import gal.subtitula.api.transparency.guide.GuideDecisionRepository;
import gal.subtitula.api.transparency.guide.GuideEvidenceLink;
import gal.subtitula.api.transparency.guide.GuideEvidenceLinkRepository;
import gal.subtitula.api.transparency.guide.GuideTopic;
import gal.subtitula.api.transparency.guide.GuideTopicRepository;
import gal.subtitula.api.transparency.guide.SessionGuide;
import gal.subtitula.api.transparency.guide.SessionGuideRepository;
import gal.subtitula.api.transparency.lifecycle.InstitutionalProjectStatus;
import gal.subtitula.api.transparency.model.AgendaSource;
import gal.subtitula.api.transparency.model.AgendaVisibility;
import gal.subtitula.api.transparency.model.ContributionKind;
import gal.subtitula.api.transparency.model.GuideEvidenceSubjectType;
import gal.subtitula.api.transparency.recording.Recording;
import gal.subtitula.api.transparency.recording.RecordingRepository;
import gal.subtitula.api.transparency.transcript.EvidenceSegment;
import gal.subtitula.api.transparency.transcript.EvidenceSegmentRepository;
import gal.subtitula.api.transparency.transcript.Speaker;
import gal.subtitula.api.transparency.transcript.SpeakerRepository;
import gal.subtitula.api.transparency.transcript.TranscriptRevision;
import gal.subtitula.api.transparency.transcript.TranscriptRevisionRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
    "app.capabilities.durable-institutional-upload=true",
    "app.capabilities.exception-review=true",
    "app.capabilities.public-publication=true",
    "app.capabilities.lexical-search=true",
    "app.capabilities.hybrid-search=true",
    "app.search.analytics-hmac-secret=search-test-secret"
})
class PublicationSnapshotBoundaryTest extends AbstractIntegrationTest {

    private static final HexFormat HEX = HexFormat.of();

    @Autowired
    ObjectMapper mapper;
    @Autowired
    ProjectRepository projects;
    @Autowired
    RecordingRepository recordings;
    @Autowired
    TranscriptRevisionRepository revisions;
    @Autowired
    SpeakerRepository speakers;
    @Autowired
    EvidenceSegmentRepository segments;
    @Autowired
    AgendaItemRepository agendaItems;
    @Autowired
    SessionGuideRepository guides;
    @Autowired
    GuideTopicRepository topics;
    @Autowired
    GuideContributionRepository contributions;
    @Autowired
    GuideDecisionRepository decisions;
    @Autowired
    GuideEvidenceLinkRepository evidenceLinks;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void publicationPinsEvidenceAndCorrectionsCreateNewVersions() throws Exception {
        Cookie owner = registerAndSession("publication-owner@example.com");
        Cookie outsider = registerAndSession("publication-outsider@example.com");
        JsonNode draft = createDraft(owner);
        UUID projectId = UUID.fromString(draft.get("id").asText());
        var project = projects.findById(projectId).orElseThrow();
        project.transitionTo(InstitutionalProjectStatus.UPLOADING);
        project.transitionTo(InstitutionalProjectStatus.UPLOADED);
        project.transitionTo(InstitutionalProjectStatus.TRANSCRIBING);
        project.transitionTo(InstitutionalProjectStatus.REVIEW_REQUIRED);
        project.transitionTo(InstitutionalProjectStatus.ENRICHING);
        project.transitionTo(InstitutionalProjectStatus.READY);
        projects.saveAndFlush(project);

        UUID recordingId = UUID.randomUUID();
        Recording recording = Recording.createUploadIntent(
            recordingId,
            projectId,
            "test/organizations/personal/projects/" + projectId
                + "/recordings/" + recordingId + "/original",
            "pleno.mp4",
            "video/mp4",
            10_000,
            null,
            true);
        recording.verify("\"recording-etag\"");
        recordings.saveAndFlush(recording);

        TranscriptRevision revision = TranscriptRevision.createAsr(
            projectId,
            1,
            "elevenlabs",
            "scribe_v2",
            "glg",
            null,
            "test/raw/transcript.json",
            "1".repeat(64));
        revisions.saveAndFlush(revision);
        Speaker speaker = speakers.saveAndFlush(
            Speaker.createUnknown(projectId, "speaker_0"));
        EvidenceSegment segment = segments.saveAndFlush(EvidenceSegment.create(
            revision.getId(),
            0,
            0,
            3_000,
            speaker.getId(),
            "A proposta queda aprobada por unanimidade.",
            null,
            null));
        revision.freeze(project.getUserId(), "2".repeat(64));
        revisions.saveAndFlush(revision);

        AgendaItem agenda = agendaItems.saveAndFlush(AgendaItem.create(
            projectId,
            0,
            "2",
            "Orzamento municipal",
            null,
            AgendaSource.MANUAL,
            AgendaVisibility.PUBLIC));
        SessionGuide guide = guides.saveAndFlush(SessionGuide.create(
            projectId,
            revision.getId(),
            1,
            "1.0.0",
            "test-model",
            "guide-v1",
            "3".repeat(64),
            "test/guides/3.json",
            false));
        UUID topicId = UUID.randomUUID();
        GuideTopic topic = topics.saveAndFlush(GuideTopic.create(
            topicId,
            guide.getId(),
            0,
            "Aprobación da proposta",
            "A proposta foi aprobada por unanimidade.",
            null,
            agenda.getId(),
            segment.getId(),
            segment.getId()));
        UUID contributionId = UUID.randomUUID();
        contributions.saveAndFlush(GuideContribution.create(
            contributionId,
            topic.getId(),
            0,
            speaker.getId(),
            ContributionKind.EXPLANATION,
            "Presentouse o resultado da votación."));
        UUID candidateId = UUID.randomUUID();
        GuideDecision candidate = decisions.save(GuideDecision.candidate(
            candidateId,
            topic.getId(),
            agenda.getId(),
            0,
            "Posible acordo non confirmado.",
            null,
            null,
            null));
        UUID confirmedId = UUID.randomUUID();
        GuideDecision confirmed = GuideDecision.candidate(
            confirmedId,
            topic.getId(),
            agenda.getId(),
            1,
            "A proposta foi aprobada por unanimidade.",
            null,
            "Aprobada",
            null);
        confirmed.confirm(project.getUserId(), null);
        decisions.saveAndFlush(confirmed);
        evidenceLinks.save(GuideEvidenceLink.create(
            guide.getId(),
            GuideEvidenceSubjectType.TOPIC,
            topicId,
            segment.getId(),
            "SUPPORT",
            0));
        evidenceLinks.save(GuideEvidenceLink.create(
            guide.getId(),
            GuideEvidenceSubjectType.CONTRIBUTION,
            contributionId,
            segment.getId(),
            "SUPPORT",
            0));
        evidenceLinks.save(GuideEvidenceLink.create(
            guide.getId(),
            GuideEvidenceSubjectType.DECISION,
            candidateId,
            segment.getId(),
            "SUPPORT",
            0));
        evidenceLinks.saveAndFlush(GuideEvidenceLink.create(
            guide.getId(),
            GuideEvidenceSubjectType.DECISION,
            confirmedId,
            segment.getId(),
            "SUPPORT",
            0));

        String documentBody = mapper.createObjectNode()
            .put("type", "agreement")
            .put("officialUrl", "https://example.gal/acordos/42")
            .put("title", "Acordo 42/2026")
            .put("issuingBody", "Concello de exemplo")
            .put("documentDate", "2026-07-29")
            .put("publicationPermission", true)
            .toString();
        JsonNode document = json(mockMvc.perform(post(
                "/projects/" + projectId + "/documents")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(documentBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
        mockMvc.perform(get(
                "/projects/" + projectId + "/publication-checklist")
                .cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publishable").value(true))
            .andExpect(jsonPath("$.items[0].required").value(true));
        mockMvc.perform(get(
                "/projects/" + projectId + "/publication-checklist")
                .cookie(outsider))
            .andExpect(status().isNotFound());

        JsonNode projectView = project(owner, projectId);
        var publishCommand = mapper.createObjectNode()
            .put("recordingId", recordingId.toString())
            .put("desiredSlug", "pleno-xullo-2026")
            .putNull("correctionNote")
            .put("expectedProjectVersion", projectView.get("version").asLong());
        publishCommand.putArray("documentIds").add(document.get("id").asText());
        String publishBody = publishCommand.toString();
        JsonNode firstPublication = json(mockMvc.perform(post(
                "/projects/" + projectId + "/publications")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(publishBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.versionNumber").value(1))
            .andReturn().getResponse().getContentAsString());
        String slug = firstPublication.get("slug").asText();
        String publicationId = firstPublication.get("id").asText();
        String indexJobId = firstPublication.get("indexJobId").asText();
        assertThat(jdbc.queryForObject(
            "select state from processing_jobs where id = ?",
            String.class,
            UUID.fromString(indexJobId))).isEqualTo("QUEUED");
        mockMvc.perform(get(
                "/projects/" + projectId + "/publications/latest")
                .cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(publicationId))
            .andExpect(jsonPath("$.state").value("published"));
        mockMvc.perform(get(
                "/projects/" + projectId + "/publications/latest")
                .cookie(outsider))
            .andExpect(status().isNotFound());

        String embeddingBase = "/internal/processing/jobs/" + indexJobId
            + "/publications/" + publicationId;
        String workflowInstanceId = "test-index-" + UUID.randomUUID();
        String lexicalBody = mapper.createObjectNode()
            .put("projectId", projectId.toString())
            .put("workflowInstanceId", workflowInstanceId)
            .toString();
        mockMvc.perform(signed(
                "POST",
                embeddingBase + "/lexical-workflow",
                lexicalBody))
            .andExpect(status().isOk());
        mockMvc.perform(signed(
                "POST",
                embeddingBase + "/lexical-index",
                lexicalBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.semanticIndexRequired").value(true));
        JsonNode embeddingContext = json(mockMvc.perform(signed(
                "GET",
                embeddingBase + "/embedding-context",
                ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dimensions").value(1024))
            .andExpect(jsonPath("$.documents").isNotEmpty())
            .andReturn().getResponse().getContentAsString());
        String embeddingStartBody = mapper.createObjectNode()
            .put("workflowInstanceId", workflowInstanceId)
            .put("expectedJobVersion", embeddingContext.get("jobVersion").asLong())
            .put("expectedProjectVersion", embeddingContext.get("projectVersion").asLong())
            .toString();
        mockMvc.perform(signed(
                "POST",
                embeddingBase + "/embedding-start",
                embeddingStartBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("running"))
            .andExpect(jsonPath("$.stage").value("generating_embeddings"));

        var embeddingBatch = mapper.createObjectNode()
            .put("workflowInstanceId", workflowInstanceId)
            .put("modelVersion", embeddingContext.get("modelVersion").asText())
            .put("dimensions", 1024);
        var embeddingItems = embeddingBatch.putArray("items");
        for (JsonNode searchDocument : embeddingContext.get("documents")) {
            var item = embeddingItems.addObject()
                .put("searchDocumentId", searchDocument.get("id").asText())
                .put("contentHash", searchDocument.get("contentHash").asText());
            item.set("embedding", unitVector());
        }
        String embeddingBatchBody = embeddingBatch.toString();
        mockMvc.perform(signed(
                "POST",
                embeddingBase + "/embeddings",
                embeddingBatchBody))
            .andExpect(status().isNoContent());
        String embeddingCompleteBody = mapper.createObjectNode()
            .put("workflowInstanceId", workflowInstanceId)
            .put("modelVersion", embeddingContext.get("modelVersion").asText())
            .toString();
        mockMvc.perform(signed(
                "POST",
                embeddingBase + "/embedding-complete",
                embeddingCompleteBody))
            .andExpect(status().isNoContent());

        var hybridCommand = mapper.createObjectNode()
            .put("query", "renovación da rede de abastecemento")
            .put("publicSlug", slug)
            .putNull("organizationId")
            .putNull("sessionBody")
            .putNull("dateFrom")
            .putNull("dateTo")
            .putNull("speakerId")
            .putNull("agendaItemId")
            .putNull("language")
            .putNull("kind")
            .put("limit", 20)
            .put("offset", 0);
        hybridCommand.set("embedding", unitVector());
        String hybridBody = hybridCommand.toString();
        String hybridResponse = mockMvc.perform(signed(
                "POST",
                "/internal/processing/search/hybrid",
                hybridBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("HYBRID"))
            .andExpect(jsonPath("$.total")
                .value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.results[0].publicSlug").value(slug))
            .andExpect(jsonPath("$.results[0].matchReasons")
                .value(org.hamcrest.Matchers.hasItem("RELATED_MEANING")))
            .andReturn().getResponse().getContentAsString();
        assertThat(hybridResponse).doesNotContain("score", "embedding", "objectKey");
        assertThat(jdbc.queryForObject(
            "select count(*) from search_query_events where search_mode = 'HYBRID'",
            Integer.class)).isEqualTo(1);

        JsonNode exactSearch = json(mockMvc.perform(get("/public/search")
                .param("q", "orzamento municipal")
                .param("body", "Concello de exemplo")
                .param("dateFrom", "2026-01-01")
                .param("dateTo", "2026-12-31")
                .param("language", "glg"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("LEXICAL"))
            .andExpect(jsonPath("$.total")
                .value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.results[0].publicSlug").value(slug))
            .andReturn().getResponse().getContentAsString());
        assertThat(exactSearch.get("queryEventId").asText()).isNotBlank();
        assertThat(exactSearch.toString())
            .doesNotContain("publicRank", "score", "objectKey", "provider");
        mockMvc.perform(post("/public/search/clicks")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.createObjectNode()
                    .put("queryEventId", exactSearch.get("queryEventId").asText())
                    .put(
                        "searchDocumentId",
                        exactSearch.get("results").get(0).get("searchDocumentId").asText())
                    .put("resultRank", 1)
                    .toString()))
            .andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject(
            "select count(*) from search_click_events",
            Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/projects/" + projectId + "/pilot-metrics")
                .cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicSearch.attributedQueryCount").value(2))
            .andExpect(jsonPath("$.publicSearch.hybridQueryCount").value(1))
            .andExpect(jsonPath("$.publicSearch.evidenceClickCount").value(1))
            .andExpect(jsonPath(
                "$.publicSearch.estimatedHybridQueryCost[0].microunits")
                .value(org.hamcrest.Matchers.greaterThan(0)));

        JsonNode reindex = json(mockMvc.perform(post(
                "/projects/" + projectId + "/publications/"
                    + publicationId + "/reindex")
                .with(csrf())
                .cookie(owner))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.publicationId").value(publicationId))
            .andExpect(jsonPath("$.semanticIndexRequired").value(true))
            .andReturn().getResponse().getContentAsString());
        String reindexContextPath = "/internal/processing/jobs/"
            + reindex.get("jobId").asText()
            + "/publications/" + publicationId;
        String reindexWorkflow = "test-reindex-" + UUID.randomUUID();
        String reindexLexicalBody = mapper.createObjectNode()
            .put("projectId", projectId.toString())
            .put("workflowInstanceId", reindexWorkflow)
            .toString();
        mockMvc.perform(signed(
                "POST",
                reindexContextPath + "/lexical-workflow",
                reindexLexicalBody))
            .andExpect(status().isOk());
        mockMvc.perform(signed(
                "POST",
                reindexContextPath + "/lexical-index",
                reindexLexicalBody))
            .andExpect(status().isOk());
        mockMvc.perform(signed("GET", reindexContextPath + "/embedding-context", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documents").isNotEmpty());
        assertThat(jdbc.queryForObject(
            "select state from processing_jobs where id = ?",
            String.class,
            UUID.fromString(reindex.get("jobId").asText())))
            .isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject(
            "select count(*) from search_click_events",
            Integer.class)).isEqualTo(1);
        mockMvc.perform(post(
                "/projects/" + projectId + "/publications/"
                    + publicationId + "/reindex")
                .with(csrf())
                .cookie(outsider))
            .andExpect(status().isNotFound());
        String storedHmac = jdbc.queryForObject(
            "select query_hmac from search_query_events order by created_at limit 1",
            String.class);
        assertThat(storedHmac)
            .hasSize(64)
            .doesNotContain("orzamento", "municipal");
        assertThat(jdbc.queryForObject(
            "select count(*) from search_documents where active and document_kind = 'DECISION'",
            Integer.class)).isEqualTo(1);

        mockMvc.perform(get("/public/search")
                .param("q", "cando se falou da proposta aprobada?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total")
                .value(org.hamcrest.Matchers.greaterThan(0)));
        mockMvc.perform(get("/public/search")
                .param("q", "unanimiddae"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total")
                .value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.results[0].matchReasons")
                .isNotEmpty());
        mockMvc.perform(get("/public/sessions/" + slug + "/search")
                .param("q", "proposta")
                .param("kind", "evidence"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total")
                .value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.results[0].kind").value("EVIDENCE"));
        mockMvc.perform(get("/public/search").param("q", " "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_search"));

        String publicBody = mockMvc.perform(get("/public/sessions/" + slug))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=60")))
            .andExpect(jsonPath("$.publicationVersion").value(1))
            .andExpect(jsonPath("$.transcript[0].text")
                .value("A proposta queda aprobada por unanimidade."))
            .andExpect(jsonPath("$.guide.topics[0].decisions.length()").value(1))
            .andExpect(jsonPath("$.guide.topics[0].decisions[0].status")
                .value("confirmed"))
            .andExpect(jsonPath("$.documents[0].officialUrl")
                .value("https://example.gal/acordos/42"))
            .andReturn().getResponse().getContentAsString();
        assertThat(publicBody).doesNotContain("objectKey", "userId", "providerLabel");
        mockMvc.perform(get("/public/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value(slug));

        String mediaPath = "/internal/processing/publications/" + slug
            + "/versions/1/media";
        mockMvc.perform(get(mediaPath)).andExpect(status().isUnauthorized());
        mockMvc.perform(signed("GET", mediaPath, ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.objectKey").value(recording.getObjectKey()))
            .andExpect(jsonPath("$.mimeType").value("video/mp4"));

        JsonNode publishedProject = project(owner, projectId);
        String correctionBody = mapper.createObjectNode()
            .put("expectedProjectVersion", publishedProject.get("version").asLong())
            .toString();
        mockMvc.perform(post("/projects/" + projectId + "/corrections")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transcriptVersionNumber").value(2))
            .andExpect(jsonPath("$.projectStatus").value("review_required"));

        JsonNode correctionTranscript = json(mockMvc.perform(
                get("/projects/" + projectId + "/transcript").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision.source").value("correction"))
            .andReturn().getResponse().getContentAsString());
        JsonNode reviewSession = json(mockMvc.perform(
                post("/projects/" + projectId + "/review/open")
                    .with(csrf())
                    .cookie(owner))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        JsonNode correctionSegment = correctionTranscript.get("segments").get(0);
        String editBody = mapper.createObjectNode()
            .put("text", "A proposta queda aprobada por maioría.")
            .put("speakerId", speaker.getId().toString())
            .put("expectedVersion", correctionSegment.get("version").asLong())
            .put("reviewSessionId", reviewSession.get("id").asText())
            .toString();
        mockMvc.perform(patch(
                "/projects/" + projectId + "/segments/" + correctionSegment.get("id").asText())
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody))
            .andExpect(status().isOk());
        JsonNode correctionQueue = json(mockMvc.perform(
                get("/projects/" + projectId + "/review-issues").cookie(owner))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        String completeBody = mapper.createObjectNode()
            .put("reviewSessionId", reviewSession.get("id").asText())
            .put("expectedProjectVersion", correctionQueue.get("projectVersion").asLong())
            .put("expectedRevisionVersion", correctionQueue.get("revisionVersion").asLong())
            .toString();
        mockMvc.perform(post("/projects/" + projectId + "/review/complete")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectStatus").value("ready"));

        JsonNode readyCorrection = project(owner, projectId);
        var correctedPublication = mapper.createObjectNode()
            .put("recordingId", recordingId.toString())
            .put("correctionNote", "Corrección dunha expresión da votación.")
            .put("expectedProjectVersion", readyCorrection.get("version").asLong());
        correctedPublication.putArray("documentIds").add(document.get("id").asText());
        JsonNode secondPublication = json(mockMvc.perform(post(
                "/projects/" + projectId + "/publications")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctedPublication.toString()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value(slug))
            .andExpect(jsonPath("$.versionNumber").value(2))
            .andReturn().getResponse().getContentAsString());

        String secondIndexBase = "/internal/processing/jobs/"
            + secondPublication.get("indexJobId").asText()
            + "/publications/" + secondPublication.get("id").asText();
        String secondIndexWorkflow = "test-index-" + UUID.randomUUID();
        String secondLexicalBody = mapper.createObjectNode()
            .put("projectId", projectId.toString())
            .put("workflowInstanceId", secondIndexWorkflow)
            .toString();
        mockMvc.perform(signed(
                "POST",
                secondIndexBase + "/lexical-workflow",
                secondLexicalBody))
            .andExpect(status().isOk());
        mockMvc.perform(signed(
                "POST",
                secondIndexBase + "/lexical-index",
                secondLexicalBody))
            .andExpect(status().isOk());

        mockMvc.perform(get("/public/sessions/" + slug))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicationVersion").value(2))
            .andExpect(jsonPath("$.transcript[0].text")
                .value("A proposta queda aprobada por maioría."))
            .andExpect(jsonPath("$.guide").doesNotExist());
        mockMvc.perform(get("/public/search").param("q", "unanimidade"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/public/search").param("q", "maioría"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total")
                .value(org.hamcrest.Matchers.greaterThan(0)))
            .andExpect(jsonPath("$.results[0].publicationVersion").value(2));
        assertThat(jdbc.queryForObject(
            "select count(distinct publication_id) from search_documents where active",
            Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/public/sessions/" + slug).param("version", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transcript[0].text")
                .value("A proposta queda aprobada por unanimidade."));

        String withdrawBody = mapper.createObjectNode()
            .put("expectedVersion", secondPublication.get("version").asLong())
            .toString();
        mockMvc.perform(post(
                "/projects/" + projectId + "/publications/"
                    + secondPublication.get("id").asText() + "/withdraw")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(withdrawBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("withdrawn"));
        mockMvc.perform(get("/public/sessions/" + slug))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/public/search").param("q", "maioría"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(
                "/projects/" + projectId + "/publications/latest")
                .cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("withdrawn"));
    }

    private JsonNode project(Cookie owner, UUID projectId) throws Exception {
        return json(mockMvc.perform(get("/projects/" + projectId).cookie(owner))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode createDraft(Cookie owner) throws Exception {
        String body = mapper.createObjectNode()
            .put("name", "Pleno de xullo")
            .put("language", "glg")
            .put("sessionDate", "2026-07-29")
            .put("body", "Concello de exemplo")
            .put("location", "Casa do Concello")
            .put("sessionType", "ordinary")
            .toString();
        return json(mockMvc.perform(post("/projects")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }

    private ArrayNode unitVector() {
        ArrayNode vector = mapper.createArrayNode();
        vector.add(1.0);
        for (int index = 1; index < 1024; index++) {
            vector.add(0.0);
        }
        return vector;
    }

    private MockHttpServletRequestBuilder signed(
            String method,
            String path,
            String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String digest = HEX.formatHex(
            MessageDigest.getInstance("SHA-256").digest(bytes));
        Instant timestamp = Instant.now();
        UUID nonce = UUID.randomUUID();
        String canonical = method.toUpperCase()
            + "\n" + path
            + "\n" + timestamp.getEpochSecond()
            + "\n" + nonce
            + "\n" + digest;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
            INTERNAL_HMAC_SECRET.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"));
        String signature = HEX.formatHex(
            mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        return request(HttpMethod.valueOf(method), path)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .header("X-Subtitula-Timestamp", timestamp.getEpochSecond())
            .header("X-Subtitula-Nonce", nonce)
            .header("X-Subtitula-Content-SHA256", digest)
            .header("X-Subtitula-Signature", signature);
    }
}
