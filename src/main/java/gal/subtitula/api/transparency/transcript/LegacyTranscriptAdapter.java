package gal.subtitula.api.transparency.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.Word;
import gal.subtitula.api.transparency.transcript.dto.EvidenceSegmentResponse;
import gal.subtitula.api.transparency.transcript.dto.SpeakerResponse;
import gal.subtitula.api.transparency.transcript.dto.TranscriptResponse;
import gal.subtitula.api.transparency.transcript.dto.TranscriptRevisionResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
class LegacyTranscriptAdapter {

    private static final int MAX_SEGMENT_CHARACTERS = 500;
    private static final long MAX_SEGMENT_DURATION_MS = 30_000;
    private final ObjectMapper mapper;

    LegacyTranscriptAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    TranscriptResponse adapt(Project project) {
        UUID revisionId = stableId(project.getId(), "legacy-revision");
        UUID speakerId = stableId(project.getId(), "legacy-speaker");
        var revision = new TranscriptRevisionResponse(
            revisionId,
            0,
            "working",
            "legacy",
            project.getLanguage(),
            project.getVersion());
        var speaker = new SpeakerResponse(
            speakerId,
            null,
            "Persoa non identificada",
            null,
            null,
            "unknown",
            "legacy",
            0);
        var segments = toSegments(project, speakerId);
        return new TranscriptResponse(
            revision,
            segments.isEmpty() ? List.of() : List.of(speaker),
            segments,
            true);
    }

    private List<EvidenceSegmentResponse> toSegments(Project project, UUID speakerId) {
        List<Word> words = project.getWords() == null ? List.of() : project.getWords();
        var result = new ArrayList<EvidenceSegmentResponse>();
        var current = new ArrayList<Word>();

        for (Word word : words) {
            current.add(word);
            if (shouldEnd(current, word)) {
                addSegment(project.getId(), speakerId, result, current);
                current = new ArrayList<>();
            }
        }
        addSegment(project.getId(), speakerId, result, current);
        return List.copyOf(result);
    }

    private boolean shouldEnd(List<Word> current, Word latest) {
        String text = joinedText(current);
        if (text.length() >= MAX_SEGMENT_CHARACTERS) {
            return !"spacing".equals(latest.type());
        }
        long start = toMilliseconds(current.get(0).start());
        long end = toMilliseconds(latest.end());
        if (end - start >= MAX_SEGMENT_DURATION_MS && !"spacing".equals(latest.type())) {
            return true;
        }
        return "word".equals(latest.type())
            && latest.text() != null
            && latest.text().matches(".*[.!?…][\\\"'»)]?$");
    }

    private void addSegment(
            UUID projectId,
            UUID speakerId,
            List<EvidenceSegmentResponse> result,
            List<Word> sourceWords) {
        String text = joinedText(sourceWords).strip();
        if (text.isEmpty()) {
            return;
        }
        int sequence = result.size();
        ArrayNode timings = mapper.createArrayNode();
        boolean missingTiming = false;
        long startMs = Long.MAX_VALUE;
        long endMs = 0;
        for (int index = 0; index < sourceWords.size(); index++) {
            Word word = sourceWords.get(index);
            long wordStartMs = toMilliseconds(word.start());
            long wordEndMs = toMilliseconds(word.end());
            if (wordStartMs < 0 || wordEndMs < wordStartMs) {
                missingTiming = true;
            }
            startMs = Math.min(startMs, Math.max(0, wordStartMs));
            endMs = Math.max(endMs, Math.max(0, wordEndMs));
            ObjectNode timing = timings.addObject();
            timing.put("sourceWordId", "w" + index);
            timing.put("text", word.text());
            timing.put("startMs", Math.max(0, wordStartMs));
            timing.put("endMs", Math.max(0, wordEndMs));
            timing.put("type", word.type());
            timing.putNull("speakerProviderLabel");
            timing.putNull("logProbability");
        }
        if (startMs == Long.MAX_VALUE) {
            startMs = 0;
            missingTiming = true;
        }
        ObjectNode signals = mapper.createObjectNode();
        signals.put("speakerChanged", false);
        signals.put("hasLowLogProbability", false);
        signals.put("hasAudioEvent", sourceWords.stream()
            .anyMatch(word -> "audio_event".equals(word.type())));
        signals.put("hasMissingTiming", missingTiming);
        signals.putArray("properNameCandidates");
        result.add(new EvidenceSegmentResponse(
            stableId(projectId, "legacy-segment-" + sequence),
            sequence,
            startMs,
            endMs,
            speakerId,
            text,
            timings,
            "unreviewed",
            signals,
            0));
    }

    private static String joinedText(List<Word> words) {
        var builder = new StringBuilder();
        for (Word word : words) {
            if (word.text() != null) {
                builder.append(word.text());
            }
        }
        return builder.toString();
    }

    private static long toMilliseconds(double seconds) {
        if (!Double.isFinite(seconds)) {
            return -1;
        }
        return Math.round(seconds * 1000);
    }

    private static UUID stableId(UUID projectId, String suffix) {
        String value = projectId.toString().toLowerCase(Locale.ROOT) + ":" + suffix;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
