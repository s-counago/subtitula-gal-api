package gal.subtitula.api.transparency.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gal.subtitula.api.project.ProjectRepository;
import gal.subtitula.api.support.AbstractIntegrationTest;
import gal.subtitula.api.transparency.lifecycle.InstitutionalProjectStatus;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import gal.subtitula.api.transparency.processing.ProcessingJob;
import gal.subtitula.api.transparency.processing.ProcessingJobRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(properties = {
    "app.capabilities.durable-institutional-upload=true",
    "app.capabilities.exception-review=true",
    "app.capabilities.structured-guide=true",
    "app.capabilities.automatic-agenda=true"
})
class StructuredGuideBoundaryTest extends AbstractIntegrationTest {

    private static final HexFormat HEX = HexFormat.of();

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ProjectRepository projects;

    @Autowired
    TranscriptRevisionRepository revisions;

    @Autowired
    SpeakerRepository speakers;

    @Autowired
    EvidenceSegmentRepository segments;

    @Autowired
    ProcessingJobRepository jobs;

    @Test
    void automaticGuideRequiresFrozenEvidenceAndKeepsHumanChecksSmall()
            throws Exception {
        Cookie owner = registerAndSession("guide-owner@example.com");
        Cookie outsider = registerAndSession("guide-outsider@example.com");
        JsonNode draft = createDraft(owner);
        UUID projectId = UUID.fromString(draft.get("id").asText());
        var project = projects.findById(projectId).orElseThrow();
        project.transitionTo(InstitutionalProjectStatus.UPLOADING);
        project.transitionTo(InstitutionalProjectStatus.UPLOADED);
        project.transitionTo(InstitutionalProjectStatus.TRANSCRIBING);
        project.transitionTo(InstitutionalProjectStatus.REVIEW_REQUIRED);
        projects.saveAndFlush(project);

        String transcriptHash = "c".repeat(64);
        TranscriptRevision revision = revisions.saveAndFlush(
            TranscriptRevision.createAsr(
                projectId,
                1,
                "elevenlabs",
                "scribe_v2",
                "glg",
                null,
                "test/raw/transcript.json",
                transcriptHash));
        Speaker speaker = speakers.saveAndFlush(
            Speaker.createUnknown(projectId, "speaker_0"));
        EvidenceSegment budget = segments.save(EvidenceSegment.create(
            revision.getId(),
            0,
            0,
            2_000,
            speaker.getId(),
            "Debátese o orzamento municipal.",
            null,
            null));
        EvidenceSegment decisionEvidence = segments.saveAndFlush(EvidenceSegment.create(
            revision.getId(),
            1,
            2_000,
            4_000,
            speaker.getId(),
            "A proposta queda aprobada por unanimidade.",
            null,
            null));

        String agendaBody = mapper.createObjectNode()
            .put("pastedText", "1. Orzamento municipal\n2. Rede de auga")
            .toString();
        JsonNode agenda = json(mockMvc.perform(put("/projects/" + projectId + "/agenda")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(agendaBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn().getResponse().getContentAsString());
        mockMvc.perform(get("/projects/" + projectId + "/agenda").cookie(outsider))
            .andExpect(status().isNotFound());

        JsonNode session = json(mockMvc.perform(
                post("/projects/" + projectId + "/review/open")
                    .with(csrf())
                    .cookie(owner))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        JsonNode queue = json(mockMvc.perform(
                get("/projects/" + projectId + "/review-issues").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openRequired").value(0))
            .andReturn().getResponse().getContentAsString());
        String completeBody = mapper.createObjectNode()
            .put("reviewSessionId", session.get("id").asText())
            .put("expectedProjectVersion", queue.get("projectVersion").asLong())
            .put("expectedRevisionVersion", queue.get("revisionVersion").asLong())
            .toString();
        JsonNode completed = json(mockMvc.perform(
                post("/projects/" + projectId + "/review/complete")
                    .with(csrf())
                    .cookie(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(completeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectStatus").value("enriching"))
            .andExpect(jsonPath("$.enrichmentJobId").isString())
            .andReturn().getResponse().getContentAsString());
        UUID jobId = UUID.fromString(completed.get("enrichmentJobId").asText());

        String contextPath =
            "/internal/processing/jobs/" + jobId + "/enrichment-context";
        JsonNode context = json(mockMvc.perform(signed("GET", contextPath, ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transcriptRevisionId")
                .value(revision.getId().toString()))
            .andExpect(jsonPath("$.segments.length()").value(2))
            .andExpect(jsonPath("$.agendaItems.length()").value(2))
            .andReturn().getResponse().getContentAsString());

        String startPath =
            "/internal/processing/jobs/" + jobId + "/enrichment-start";
        String startBody = mapper.createObjectNode()
            .put("workflowInstanceId", "enrich-" + jobId)
            .put("expectedJobVersion", context.get("jobVersion").asLong())
            .put("expectedProjectVersion", context.get("projectVersion").asLong())
            .toString();
        JsonNode started = json(mockMvc.perform(signed("POST", startPath, startBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stage").value("aligning_agenda"))
            .andReturn().getResponse().getContentAsString());

        UUID topicId = UUID.randomUUID();
        UUID contributionId = UUID.randomUUID();
        UUID decisionId = UUID.randomUUID();
        String guidePath = "/internal/processing/jobs/" + jobId + "/guide";
        var guide = mapper.createObjectNode()
            .put("transcriptRevisionId", revision.getId().toString())
            .put("schemaVersion", "1.0.0")
            .put("generationModel", "test-multilingual-model")
            .put("promptVersion", "guide-v1")
            .put("contentHash", "d".repeat(64))
            .put("rawArtifactKey", "test/guides/validated/" + "d".repeat(64) + ".json")
            .put("expectedJobVersion", started.get("jobVersion").asLong())
            .put("expectedProjectVersion", started.get("projectVersion").asLong());
        var alignment = guide.putArray("alignments").addObject()
            .put("agendaItemId", agenda.get("items").get(0).get("id").asText())
            .put("occurrence", 0)
            .put("startSegmentId", budget.getId().toString())
            .put("endSegmentId", decisionEvidence.getId().toString())
            .put("state", "automatic")
            .put("requiresHumanCheck", true)
            .put("algorithmVersion", "monotonic-anchors-v1")
            .put("revisited", false);
        alignment.putObject("signals").put("confidence", 0.25);
        var topic = guide.putArray("topics").addObject()
            .put("id", topicId.toString())
            .put("ordinal", 0)
            .put("title", "Orzamento municipal")
            .put("neutralSummary", "Debateuse o orzamento e aprobouse unha proposta.")
            .put("agendaItemId", agenda.get("items").get(0).get("id").asText())
            .put("startSegmentId", budget.getId().toString())
            .put("endSegmentId", decisionEvidence.getId().toString());
        topic.putArray("aliases").add("contas municipais");
        topic.putArray("evidenceSegmentIds")
            .add(budget.getId().toString())
            .add(decisionEvidence.getId().toString());
        var contribution = topic.putArray("contributions").addObject()
            .put("id", contributionId.toString())
            .put("ordinal", 0)
            .put("speakerId", speaker.getId().toString())
            .put("kind", "explanation")
            .put("neutralSummary", "Explicou a proposta orzamentaria.")
            .put("explicitClassification", true);
        contribution.putArray("evidenceSegmentIds").add(budget.getId().toString());
        var decision = topic.putArray("decisions").addObject()
            .put("id", decisionId.toString())
            .put("ordinal", 0)
            .put("agendaItemId", agenda.get("items").get(0).get("id").asText())
            .put("neutralDescription", "A proposta foi aprobada por unanimidade.")
            .put("motion", "Aprobar a proposta")
            .put("result", "Aprobada")
            .putNull("voteDetails");
        decision.putArray("evidenceSegmentIds")
            .add(decisionEvidence.getId().toString());

        JsonNode fabricated = guide.deepCopy();
        var fabricatedEvidence = (com.fasterxml.jackson.databind.node.ArrayNode)
            fabricated.get("topics").get(0).get("evidenceSegmentIds");
        fabricatedEvidence.removeAll().add(UUID.randomUUID().toString());
        mockMvc.perform(signed("POST", guidePath, fabricated.toString()))
            .andExpect(status().isConflict());

        JsonNode persisted = json(mockMvc.perform(
                signed("POST", guidePath, guide.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topicCount").value(1))
            .andExpect(jsonPath("$.contributionCount").value(1))
            .andExpect(jsonPath("$.candidateDecisionCount").value(1))
            .andExpect(jsonPath("$.optionalAgendaCheckCount").value(1))
            .andExpect(jsonPath("$.duplicate").value(false))
            .andReturn().getResponse().getContentAsString());
        mockMvc.perform(signed("POST", guidePath, guide.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.guideId").value(persisted.get("guideId").asText()))
            .andExpect(jsonPath("$.duplicate").value(true));

        JsonNode guideView = json(mockMvc.perform(
                get("/projects/" + projectId + "/guide").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assistedLabel").value("Resumo asistido con fontes"))
            .andExpect(jsonPath("$.topics[0].evidence.length()").value(2))
            .andExpect(jsonPath("$.topics[0].contributions[0].evidence.length()").value(1))
            .andExpect(jsonPath("$.topics[0].decisions[0].status").value("candidate"))
            .andExpect(jsonPath("$.topics[0].decisions[0].evidence.length()").value(1))
            .andReturn().getResponse().getContentAsString());
        mockMvc.perform(get("/projects/" + projectId + "/guide").cookie(outsider))
            .andExpect(status().isNotFound());

        JsonNode decisionView = guideView.get("topics").get(0).get("decisions").get(0);
        String confirmBody = mapper.createObjectNode()
            .put("action", "confirm")
            .put("neutralDescription", "A proposta foi aprobada por unanimidade.")
            .put("expectedVersion", decisionView.get("version").asLong())
            .toString();
        mockMvc.perform(patch(
                "/projects/" + projectId + "/guide/decisions/" + decisionId)
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmed"));

        JsonNode agendaView = json(mockMvc.perform(
                get("/projects/" + projectId + "/agenda").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.optionalChecks").value(1))
            .andReturn().getResponse().getContentAsString());
        JsonNode alignmentView = agendaView.get("alignments").get(0);
        String checkBody = mapper.createObjectNode()
            .put("action", "confirm")
            .put("expectedVersion", alignmentView.get("version").asLong())
            .toString();
        mockMvc.perform(patch(
                "/projects/" + projectId + "/agenda-alignments/"
                    + alignmentView.get("id").asText())
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requiresHumanCheck").value(false))
            .andExpect(jsonPath("$.state").value("confirmed"));

        mockMvc.perform(get("/projects/" + projectId + "/processing").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectStatus").value("ready"))
            .andExpect(jsonPath("$.job.state").value("succeeded"));

        // Simulate the lifecycle of a later correction. A non-duplicate payload
        // must never merge topic, contribution or confirmed-decision IDs from
        // the saved guide, even when its transcript content is unchanged.
        project = projects.findById(projectId).orElseThrow();
        project.transitionTo(InstitutionalProjectStatus.PUBLISHED);
        project.transitionTo(InstitutionalProjectStatus.REVIEW_REQUIRED);
        project.transitionTo(InstitutionalProjectStatus.ENRICHING);
        project = projects.saveAndFlush(project);
        var nextJob = ProcessingJob.queued(projectId, ProcessingJobType.ENRICH,
            "correction-" + UUID.randomUUID(), ProcessingStage.ALIGNING_AGENDA);
        nextJob.start(ProcessingStage.ALIGNING_AGENDA);
        nextJob = jobs.saveAndFlush(nextJob);
        JsonNode savedGuide = json(mockMvc.perform(
                get("/projects/" + projectId + "/guide").cookie(owner))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (int collision = 0; collision < 3; collision++) {
            var conflicting = guide.deepCopy();
            conflicting.put("contentHash", "e".repeat(64));
            conflicting.put("expectedJobVersion", nextJob.getVersion());
            conflicting.put("expectedProjectVersion", project.getVersion());
            var nextTopic = (com.fasterxml.jackson.databind.node.ObjectNode)
                conflicting.get("topics").get(0);
            nextTopic.put("id", (collision == 0 ? topicId : UUID.randomUUID()).toString());
            ((com.fasterxml.jackson.databind.node.ObjectNode) nextTopic.get("contributions").get(0))
                .put("id", (collision == 1 ? contributionId : UUID.randomUUID()).toString());
            ((com.fasterxml.jackson.databind.node.ObjectNode) nextTopic.get("decisions").get(0))
                .put("id", (collision == 2 ? decisionId : UUID.randomUUID()).toString());
            mockMvc.perform(signed("POST", "/internal/processing/jobs/"
                    + nextJob.getId() + "/guide", conflicting.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("processing_conflict"));
            assertEquals(savedGuide, json(mockMvc.perform(
                    get("/projects/" + projectId + "/guide").cookie(owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
        }
    }

    private JsonNode createDraft(Cookie owner) throws Exception {
        String body = mapper.createObjectNode()
            .put("name", "Pleno con guía")
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
