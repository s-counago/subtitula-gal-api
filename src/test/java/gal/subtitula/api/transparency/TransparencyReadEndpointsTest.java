package gal.subtitula.api.transparency;

import com.fasterxml.jackson.databind.ObjectMapper;
import gal.subtitula.api.support.AbstractIntegrationTest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransparencyReadEndpointsTest extends AbstractIntegrationTest {

    @Autowired
    TranscriptRevisionRepository revisions;

    @Autowired
    SpeakerRepository speakers;

    @Autowired
    EvidenceSegmentRepository segments;

    @Autowired
    ProcessingJobRepository jobs;

    @Autowired
    ObjectMapper mapper;

    @Test
    void legacyProjectGetsStableEvidenceAdapterWithoutRowRewrite() throws Exception {
        Cookie owner = registerAndSession("legacy-transcript@example.com");
        UUID projectId = createProject(owner, "Legacy");

        mockMvc.perform(get("/projects/" + projectId + "/transcript").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.legacyAdapter").value(true))
            .andExpect(jsonPath("$.revision.source").value("legacy"))
            .andExpect(jsonPath("$.speakers[0].identityState").value("unknown"))
            .andExpect(jsonPath("$.segments.length()").value(1))
            .andExpect(jsonPath("$.segments[0].text").value("Boas mundo"))
            .andExpect(jsonPath("$.segments[0].startMs").value(0))
            .andExpect(jsonPath("$.segments[0].endMs").value(900));
    }

    @Test
    void normalizedRevisionWinsOverLegacyWords() throws Exception {
        Cookie owner = registerAndSession("normalized-transcript@example.com");
        UUID projectId = createProject(owner, "Normalized");
        TranscriptRevision revision = revisions.saveAndFlush(TranscriptRevision.createAsr(
            projectId,
            1,
            "elevenlabs",
            "scribe_v2",
            "glg",
            "terms-v1",
            "development/projects/artifacts/provider.json",
            "a".repeat(64)));
        Speaker speaker = speakers.saveAndFlush(Speaker.createUnknown(projectId, "speaker_0"));
        var wordTimings = mapper.createArrayNode();
        wordTimings.addObject()
            .put("sourceWordId", "w0")
            .put("text", "Orzamentos")
            .put("startMs", 1000)
            .put("endMs", 1600)
            .put("type", "word")
            .put("speakerProviderLabel", "speaker_0")
            .put("logProbability", -0.01);
        var signals = mapper.createObjectNode()
            .put("speakerChanged", false)
            .put("hasLowLogProbability", false)
            .put("hasAudioEvent", false)
            .put("hasMissingTiming", false);
        signals.putArray("properNameCandidates");
        segments.saveAndFlush(EvidenceSegment.create(
            revision.getId(),
            0,
            1000,
            1600,
            speaker.getId(),
            "Orzamentos",
            wordTimings,
            signals));

        mockMvc.perform(get("/projects/" + projectId + "/transcript").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.legacyAdapter").value(false))
            .andExpect(jsonPath("$.revision.number").value(1))
            .andExpect(jsonPath("$.revision.source").value("asr"))
            .andExpect(jsonPath("$.speakers[0].providerLabel").value("speaker_0"))
            .andExpect(jsonPath("$.segments[0].text").value("Orzamentos"))
            .andExpect(jsonPath("$.segments[0].version").value(0));
    }

    @Test
    void transcriptAndProcessingAre404ForAnotherUser() throws Exception {
        Cookie owner = registerAndSession("evidence-owner@example.com");
        Cookie intruder = registerAndSession("evidence-intruder@example.com");
        UUID projectId = createProject(owner, "Private evidence");

        mockMvc.perform(get("/projects/" + projectId + "/transcript").cookie(intruder))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/projects/" + projectId + "/processing").cookie(intruder))
            .andExpect(status().isNotFound());
    }

    @Test
    void ownerSeesLatestBoundedProcessingState() throws Exception {
        Cookie owner = registerAndSession("processing-owner@example.com");
        UUID projectId = createProject(owner, "Processing");
        jobs.saveAndFlush(ProcessingJob.queued(
            projectId,
            ProcessingJobType.INGEST,
            "test-ingest-" + UUID.randomUUID(),
            ProcessingStage.QUEUED));

        mockMvc.perform(get("/projects/" + projectId + "/processing").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectStatus").value("ready"))
            .andExpect(jsonPath("$.job.type").value("ingest"))
            .andExpect(jsonPath("$.job.state").value("queued"))
            .andExpect(jsonPath("$.job.stage").value("queued"))
            .andExpect(jsonPath("$.job.retryable").value(false));
    }

    private UUID createProject(Cookie session, String name) throws Exception {
        var file = new MockMultipartFile(
            "file",
            "clip.mp4",
            "video/mp4",
            new byte[]{1, 2, 3});
        MvcResult result = mockMvc.perform(multipart("/projects")
                .file(file)
                .param("name", name)
                .with(csrf())
                .cookie(session))
            .andExpect(status().isCreated())
            .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.id");
        return UUID.fromString(id);
    }
}
