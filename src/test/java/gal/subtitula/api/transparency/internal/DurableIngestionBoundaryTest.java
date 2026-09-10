package gal.subtitula.api.transparency.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.capabilities.durable-institutional-upload=true",
    "app.capabilities.normalized-transcript=true",
    "app.capabilities.exception-review=true"
})
class DurableIngestionBoundaryTest extends AbstractIntegrationTest {

    @Test
    void institutionalDraftDefaultsToGalicianAndAcceptsOnlyGalicianOrSpanish() throws Exception {
        Cookie owner = registerAndSession("draft-languages@example.com");
        for (String language : java.util.List.of("", "glg", "spa")) {
            mockMvc.perform(post("/projects").cookie(owner).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Proba\",\"language\":\"" + language + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value(language.isEmpty() ? "glg" : language));
        }
        mockMvc.perform(post("/projects").cookie(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Proba\",\"language\":\"eng\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("unsupported_transcription_language"));
    }

    private static final HexFormat HEX = HexFormat.of();

    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void expiredUploadCleanupIsBoundedAndIdempotent() throws Exception {
        Cookie owner = registerAndSession("cleanup-owner@example.com");
        JsonNode project = createDraft(owner);
        UUID projectId = UUID.fromString(project.get("id").asText());
        UUID intentId = UUID.randomUUID();
        UUID recordingId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String createPath = "/internal/processing/upload-intents";
        String createBody = mapper.createObjectNode()
            .put("environment", "test")
            .put("projectId", projectId.toString())
            .put("intentId", intentId.toString())
            .put("recordingId", recordingId.toString())
            .put("jobId", jobId.toString())
            .put("clientRequestId", UUID.randomUUID().toString())
            .put("originalFilename", "abandonado.mp4")
            .put("mimeType", "video/mp4")
            .put("sizeBytes", 1234)
            .putNull("checksumSha256")
            .put("usagePermission", true)
            .put("expiresAt", Instant.now().plusSeconds(900).toString())
            .toString();
        mockMvc.perform(signed("POST", createPath, createBody))
            .andExpect(status().isCreated());
        jdbc.update(
            "update upload_intents set expires_at = ? where id = ?",
            java.sql.Timestamp.from(Instant.now().minusSeconds(3_600)),
            intentId);

        String candidatesPath =
            "/internal/processing/cleanup/upload-intents/candidates";
        String candidatesBody = mapper.createObjectNode()
            .put("expiredBefore", Instant.now().toString())
            .put("limit", 100)
            .toString();
        JsonNode candidates = json(mockMvc.perform(
                signed("POST", candidatesPath, candidatesBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].intentId").value(intentId.toString()))
            .andExpect(jsonPath("$[0].objectKey").isNotEmpty())
            .andReturn().getResponse().getContentAsString());
        JsonNode candidate = candidates.get(0);
        String finalizePath =
            "/internal/processing/cleanup/upload-intents/" + intentId + "/complete";
        String finalizeBody = mapper.createObjectNode()
            .put("expectedIntentVersion", candidate.get("intentVersion").asLong())
            .put("expectedRecordingVersion", candidate.get("recordingVersion").asLong())
            .toString();
        JsonNode deletion = json(mockMvc.perform(signed("POST", finalizePath, finalizeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recordingId").value(recordingId.toString()))
            .andReturn().getResponse().getContentAsString());
        mockMvc.perform(signed("POST", finalizePath, finalizeBody))
            .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select upload_state from recordings where id = ?",
            String.class,
            recordingId)).isEqualTo("EXPIRED");
        String pendingPath =
            "/internal/processing/cleanup/recordings/candidates";
        String pendingBody = mapper.createObjectNode().put("limit", 100).toString();
        mockMvc.perform(signed("POST", pendingPath, pendingBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].recordingId").value(recordingId.toString()));
        String confirmPath = "/internal/processing/cleanup/recordings/"
            + recordingId + "/complete";
        String confirmBody = mapper.createObjectNode()
            .put("expectedRecordingVersion", deletion.get("recordingVersion").asLong())
            .toString();
        mockMvc.perform(signed("POST", confirmPath, confirmBody))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/projects/" + projectId + "/processing").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectStatus").value("processing_failed"))
            .andExpect(jsonPath("$.job.state").value("failed_terminal"))
            .andExpect(jsonPath("$.job.safeErrorCode")
                .value("upload_intent_expired"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select upload_state from recordings where id = ?",
            String.class,
            recordingId)).isEqualTo("DELETED");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select state from upload_intents where id = ?",
            String.class,
            intentId)).isEqualTo("EXPIRED");
    }

    @Test
    void searchAnalyticsCleanupDeletesOnlyEventsOutsideRetention() throws Exception {
        UUID expired = UUID.randomUUID();
        UUID retained = UUID.randomUUID();
        jdbc.update("""
            insert into search_query_events (
                id, query_hmac, query_length, result_count, latency_ms,
                search_mode, filters, estimated_ai_cost_microunits,
                cost_currency, created_at
            ) values (?, ?, 8, 1, 25, 'LEXICAL', '{}'::jsonb, 0, 'USD', ?)
            """,
            expired,
            "a".repeat(64),
            java.sql.Timestamp.from(Instant.now().minusSeconds(3 * 86_400)));
        jdbc.update("""
            insert into search_query_events (
                id, query_hmac, query_length, result_count, latency_ms,
                search_mode, filters, estimated_ai_cost_microunits,
                cost_currency, created_at
            ) values (?, ?, 8, 1, 25, 'LEXICAL', '{}'::jsonb, 0, 'USD', ?)
            """,
            retained,
            "b".repeat(64),
            java.sql.Timestamp.from(Instant.now()));
        String path = "/internal/processing/cleanup/search-analytics";
        String body = mapper.createObjectNode()
            .put("createdBefore", Instant.now().minusSeconds(2 * 86_400).toString())
            .toString();
        mockMvc.perform(signed("POST", path, body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedQueryEvents").value(1));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select count(*) from search_query_events where id in (?, ?)",
            Integer.class,
            expired,
            retained)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select count(*) from search_query_events where id = ?",
            Integer.class,
            retained)).isEqualTo(1);
    }

    @Test
    void metadataFirstUploadAndTranscriptIngestionAreDurableAndIdempotent()
            throws Exception {
        Cookie owner = registerAndSession("durable-owner@example.com");
        JsonNode project = createDraft(owner);
        UUID projectId = UUID.fromString(project.get("id").asText());

        UUID intentId = UUID.randomUUID();
        UUID recordingId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();
        String createPath = "/internal/processing/upload-intents";
        String createBody = mapper.createObjectNode()
            .put("environment", "test")
            .put("projectId", projectId.toString())
            .put("intentId", intentId.toString())
            .put("recordingId", recordingId.toString())
            .put("jobId", jobId.toString())
            .put("clientRequestId", clientRequestId.toString())
            .put("originalFilename", "pleno.mp4")
            .put("mimeType", "video/mp4")
            .put("sizeBytes", 1234)
            .putNull("checksumSha256")
            .put("usagePermission", true)
            .put("expiresAt", Instant.now().plusSeconds(900).toString())
            .toString();

        JsonNode intent = json(mockMvc.perform(signed("POST", createPath, createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.state").value("created"))
            .andExpect(jsonPath("$.objectKey").value(
                "test/organizations/personal/projects/" + projectId
                    + "/recordings/" + recordingId + "/original"))
            .andReturn().getResponse().getContentAsString());

        mockMvc.perform(signed("POST", createPath, createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.intentId").value(intentId.toString()))
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.intentVersion").value(intent.get("intentVersion").asLong()));

        String workflowId = "ingest-" + intentId;
        String completePath = createPath + "/" + intentId + "/complete";
        String completeBody = mapper.createObjectNode()
            .put("etag", "\"r2-etag\"")
            .put("sizeBytes", 1234)
            .put("mimeType", "video/mp4")
            .putNull("checksumSha256")
            .put("workflowInstanceId", workflowId)
            .put("expectedIntentVersion", intent.get("intentVersion").asLong())
            .toString();
        JsonNode completed = json(mockMvc.perform(signed("POST", completePath, completeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("completed"))
            .andReturn().getResponse().getContentAsString());

        mockMvc.perform(signed("POST", completePath, completeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.intentVersion").value(completed.get("intentVersion").asLong()));

        String startPath = "/internal/processing/jobs/" + jobId + "/start";
        String startBody = mapper.createObjectNode()
            .put("expectedJobVersion", completed.get("jobVersion").asLong())
            .put("expectedProjectVersion", completed.get("projectVersion").asLong())
            .toString();
        JsonNode started = json(mockMvc.perform(signed("POST", startPath, startBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("running"))
            .andExpect(jsonPath("$.stage").value("submitting_provider"))
            .andReturn().getResponse().getContentAsString());

        Integer startedEvents = jdbc.queryForObject(
            "select count(*) from processing_events where job_id = ?",
            Integer.class, jobId);
        mockMvc.perform(signed("POST", startPath, startBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobVersion").value(started.get("jobVersion").asLong()))
            .andExpect(jsonPath("$.projectVersion").value(started.get("projectVersion").asLong()));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select count(*) from processing_events where job_id = ?",
            Integer.class, jobId)).isEqualTo(startedEvents);
        mockMvc.perform(signed("POST", startPath,
                "{\"expectedJobVersion\":0,\"expectedProjectVersion\":0}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("processing_conflict"));

        String failurePath = "/internal/processing/jobs/" + jobId + "/failed";
        String failureBody = mapper.createObjectNode()
            .put("errorCode", "provider_unavailable")
            .put("safeMessage", "O provedor non respondeu.")
            .put("expectedJobVersion", started.get("jobVersion").asLong())
            .put("expectedProjectVersion", started.get("projectVersion").asLong())
            .toString();
        mockMvc.perform(signed("POST", failurePath, failureBody))
            .andExpect(status().isNoContent());
        String contextPath = "/internal/processing/jobs/" + jobId + "/context";
        JsonNode failedContext = json(mockMvc.perform(signed("GET", contextPath, ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("failed_retryable"))
            .andReturn().getResponse().getContentAsString());
        workflowId = "ingest-retry-" + UUID.randomUUID();
        String retryBody = mapper.createObjectNode()
            .put("workflowInstanceId", workflowId)
            .put("expectedJobVersion", failedContext.get("jobVersion").asLong())
            .put("expectedProjectVersion", failedContext.get("projectVersion").asLong())
            .toString();
        started = json(mockMvc.perform(signed(
                "POST",
                "/internal/processing/jobs/" + jobId + "/ingest-retry",
                retryBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("running"))
            .andExpect(jsonPath("$.stage").value("submitting_provider"))
            .andReturn().getResponse().getContentAsString());

        // The start command from the original Workflow cannot admit a new attempt.
        mockMvc.perform(signed("POST", startPath, startBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("processing_conflict"));

        String providerRequestId = "scribe-request-123";
        String providerPath =
            "/internal/processing/jobs/" + jobId + "/provider-submitted";
        String providerBody = mapper.createObjectNode()
            .put("providerRequestId", providerRequestId)
            .put("expectedJobVersion", started.get("jobVersion").asLong())
            .toString();
        JsonNode waiting = json(mockMvc.perform(signed("POST", providerPath, providerBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("waiting"))
            .andExpect(jsonPath("$.stage").value("waiting_for_provider"))
            .andReturn().getResponse().getContentAsString());

        // Simulates an HTTP response being lost after the first command committed.
        mockMvc.perform(signed("POST", providerPath, providerBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobVersion").value(waiting.get("jobVersion").asLong()));

        String digest = "a".repeat(64);
        String artifactKey = "test/provider/elevenlabs/jobs/" + jobId + "/" + digest + ".json";
        String webhookPath =
            "/internal/processing/jobs/" + jobId + "/webhook-received";
        String webhookBody = mapper.createObjectNode()
            .put("providerRequestId", providerRequestId)
            .put("payloadDigest", digest)
            .put("artifactKey", artifactKey)
            .put("workflowInstanceId", workflowId)
            .toString();
        mockMvc.perform(signed("POST", webhookPath, webhookBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(true))
            .andExpect(jsonPath("$.duplicate").value(false));
        mockMvc.perform(signed("POST", webhookPath, webhookBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(false))
            .andExpect(jsonPath("$.duplicate").value(true));

        String inconsistentWebhook = mapper.createObjectNode()
            .put("providerRequestId", providerRequestId)
            .put("payloadDigest", "b".repeat(64))
            .put("artifactKey", artifactKey)
            .put("workflowInstanceId", workflowId)
            .toString();
        mockMvc.perform(signed("POST", webhookPath, inconsistentWebhook))
            .andExpect(status().isConflict());

        String transcriptPath =
            "/internal/processing/jobs/" + jobId + "/transcript";
        var transcript = mapper.createObjectNode()
            .put("provider", "elevenlabs")
            .put("model", "scribe_v2")
            .put("languageCode", "glg")
            .putNull("keytermVersion")
            .put("rawArtifactKey", artifactKey)
            .put("contentHash", digest)
            .put("durationMs", 4_000)
            .put("costMicrounits", 245)
            .put("costCurrency", "USD")
            .put("expectedJobVersion", waiting.get("jobVersion").asLong())
            .put("expectedProjectVersion", waiting.get("projectVersion").asLong());
        transcript.putObject("providerUsage")
            .put("audioDurationSeconds", 4)
            .put("pricingSource", "configured_estimate");
        var normalizedSpeakers = transcript.putArray("speakers");
        normalizedSpeakers.addObject().put("providerLabel", "speaker_0");
        normalizedSpeakers.addObject().put("providerLabel", "speaker_1");
        var normalizedSegments = transcript.putArray("segments");
        var segmentZero = normalizedSegments.addObject();
        segmentZero
            .put("sequence", 0)
            .put("startMs", 0)
            .put("endMs", 2_000)
            .put("speakerProviderLabel", "speaker_0")
            .put("text", "Falamos do orzamento.");
        segmentZero.putArray("wordTimings");
        segmentZero.putObject("signals").put("speakerChanged", false);
        var segmentOne = normalizedSegments.addObject();
        segmentOne
            .put("sequence", 1)
            .put("startMs", 2_000)
            .put("endMs", 4_000)
            .put("speakerProviderLabel", "speaker_1")
            .put("text", "E das tubaxes de auga.");
        segmentOne.putArray("wordTimings");
        segmentOne.putObject("signals").put("speakerChanged", true);
        String transcriptBody = transcript.toString();

        JsonNode ingested = json(mockMvc.perform(
                signed("POST", transcriptPath, transcriptBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.segmentCount").value(2))
            .andExpect(jsonPath("$.requiredIssueCount").value(2))
            .andExpect(jsonPath("$.duplicate").value(false))
            .andReturn().getResponse().getContentAsString());
        mockMvc.perform(signed("POST", transcriptPath, transcriptBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revisionId").value(ingested.get("revisionId").asText()))
            .andExpect(jsonPath("$.duplicate").value(true));

        JsonNode transcriptView = json(mockMvc.perform(
                get("/projects/" + projectId + "/transcript").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.legacyAdapter").value(false))
            .andExpect(jsonPath("$.segments.length()").value(2))
            .andExpect(jsonPath("$.segments[1].text").value("E das tubaxes de auga."))
            .andReturn().getResponse().getContentAsString());

        JsonNode queue = json(mockMvc.perform(
                get("/projects/" + projectId + "/review-issues").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openRequired").value(2))
            .andExpect(jsonPath("$.openWarnings").value(1))
            .andExpect(jsonPath("$.openByType.unknown_speaker").value(2))
            .andExpect(jsonPath("$.openByType.speaker_change").value(1))
            .andReturn().getResponse().getContentAsString());
        JsonNode reviewSession = json(mockMvc.perform(
                post("/projects/" + projectId + "/review/open")
                    .with(csrf())
                    .cookie(owner))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        String reviewSessionId = reviewSession.get("id").asText();

        JsonNode firstSpeaker = transcriptView.get("speakers").get(0);
        String speakerUpdate = mapper.createObjectNode()
            .put("confirmedName", "Ana Pérez")
            .put("role", "Concelleira")
            .put("expectedVersion", firstSpeaker.get("version").asLong())
            .put("reviewSessionId", reviewSessionId)
            .toString();
        mockMvc.perform(patch(
                "/projects/" + projectId + "/speakers/" + firstSpeaker.get("id").asText())
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(speakerUpdate))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmedName").value("Ana Pérez"))
            .andExpect(jsonPath("$.identityState").value("confirmed"))
            .andExpect(jsonPath("$.version").value(1));

        JsonNode firstSegment = transcriptView.get("segments").get(0);
        String segmentUpdate = mapper.createObjectNode()
            .put("text", "Falamos do orzamento municipal.")
            .put("speakerId", firstSpeaker.get("id").asText())
            .put("expectedVersion", firstSegment.get("version").asLong())
            .put("reviewSessionId", reviewSessionId)
            .toString();
        mockMvc.perform(patch(
                "/projects/" + projectId + "/segments/" + firstSegment.get("id").asText())
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(segmentUpdate))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("Falamos do orzamento municipal."))
            .andExpect(jsonPath("$.reviewState").value("reviewed"))
            .andExpect(jsonPath("$.version").value(1));

        for (JsonNode issue : queue.get("issues")) {
            if (!"required".equals(issue.get("severity").asText())) {
                continue;
            }
            boolean confirmedSpeaker = firstSpeaker.get("id").asText()
                .equals(issue.get("speakerId").asText());
            String resolution = mapper.createObjectNode()
                .put("resolution", confirmedSpeaker ? "confirmed" : "dismissed")
                .put("expectedVersion", issue.get("version").asLong())
                .put("reviewSessionId", reviewSessionId)
                .toString();
            mockMvc.perform(post(
                    "/projects/" + projectId + "/review-issues/"
                        + issue.get("id").asText() + "/resolve")
                    .with(csrf())
                    .cookie(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(resolution))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(
                    confirmedSpeaker ? "resolved" : "dismissed"));
        }

        JsonNode reviewedQueue = json(mockMvc.perform(
                get("/projects/" + projectId + "/review-issues").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openRequired").value(0))
            .andExpect(jsonPath("$.openWarnings").value(1))
            .andExpect(jsonPath("$.resolved").value(1))
            .andExpect(jsonPath("$.dismissed").value(1))
            .andReturn().getResponse().getContentAsString());
        String completeReview = mapper.createObjectNode()
            .put("reviewSessionId", reviewSessionId)
            .put("expectedProjectVersion", reviewedQueue.get("projectVersion").asLong())
            .put("expectedRevisionVersion", reviewedQueue.get("revisionVersion").asLong())
            .toString();
        mockMvc.perform(post("/projects/" + projectId + "/review/complete")
                .with(csrf())
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeReview))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectStatus").value("ready"))
            .andExpect(jsonPath("$.resolvedCount").value(1))
            .andExpect(jsonPath("$.dismissedCount").value(1))
            .andExpect(jsonPath("$.manualEditCount").value(2));

        mockMvc.perform(get("/projects/" + projectId + "/transcript").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.revision.state").value("frozen"))
            .andExpect(jsonPath("$.segments[0].text")
                .value("Falamos do orzamento municipal."))
            .andExpect(jsonPath("$.speakers[1].identityState").value("unknown"));
        mockMvc.perform(get("/projects/" + projectId + "/processing").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectStatus").value("ready"))
            .andExpect(jsonPath("$.job.state").value("succeeded"))
            .andExpect(jsonPath("$.job.stage").value("completed"))
            .andExpect(jsonPath("$.job.costMicrounits").value(245))
            .andExpect(jsonPath("$.job.costCurrency").value("USD"))
            .andExpect(jsonPath("$.job.startedAt").isNotEmpty())
            .andExpect(jsonPath("$.job.completedAt").isNotEmpty());

        mockMvc.perform(get("/projects/" + projectId + "/pilot-metrics").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaDurationMs").value(4_000))
            .andExpect(jsonPath("$.review.sessionCount").value(1))
            .andExpect(jsonPath("$.review.resolvedIssueCount").value(1))
            .andExpect(jsonPath("$.review.dismissedIssueCount").value(1))
            .andExpect(jsonPath("$.review.manualEditCount").value(2))
            .andExpect(jsonPath("$.processing.configuredGrossCost[0].currency")
                .value("USD"))
            .andExpect(jsonPath("$.processing.configuredGrossCost[0].microunits")
                .value(245))
            .andExpect(jsonPath("$.processing.jobs[0].costMicrounits").value(245))
            .andExpect(jsonPath("$.publicSearch.attributedQueryCount").value(0));

        Cookie outsider = registerAndSession("durable-outsider@example.com");
        mockMvc.perform(get("/projects/" + projectId + "/pilot-metrics").cookie(outsider))
            .andExpect(status().isNotFound());
    }

    @Test
    void internalCommandsRejectUnsignedTamperedExpiredAndReplayedRequests()
            throws Exception {
        String path = "/internal/processing/jobs/" + UUID.randomUUID() + "/context";

        mockMvc.perform(get(path))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("internal_auth_invalid"));

        MockHttpServletRequestBuilder tampered =
            signed("POST", "/internal/processing/upload-intents", "{}");
        tampered.content("{\"tampered\":true}");
        mockMvc.perform(tampered)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("internal_digest_invalid"));

        mockMvc.perform(signed(
                "GET",
                path,
                "",
                Instant.now().minusSeconds(600),
                UUID.randomUUID()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("internal_request_expired"));

        Instant timestamp = Instant.now();
        UUID nonce = UUID.randomUUID();
        mockMvc.perform(signed("GET", path, "", timestamp, nonce))
            .andExpect(status().isConflict());
        mockMvc.perform(signed("GET", path, "", timestamp, nonce))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("internal_request_replayed"));
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
            .andExpect(jsonPath("$.workflowMode").value("institution"))
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.sessionDate").value("2026-07-29"))
            .andReturn().getResponse().getContentAsString());
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }

    private MockHttpServletRequestBuilder signed(
            String method,
            String path,
            String body) throws Exception {
        return signed(method, path, body, Instant.now(), UUID.randomUUID());
    }

    private MockHttpServletRequestBuilder signed(
            String method,
            String path,
            String body,
            Instant timestamp,
            UUID nonce) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String digest = HEX.formatHex(
            MessageDigest.getInstance("SHA-256").digest(bytes));
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
