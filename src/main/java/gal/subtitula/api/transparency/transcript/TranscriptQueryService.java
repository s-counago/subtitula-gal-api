package gal.subtitula.api.transparency.transcript;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.transcript.dto.EvidenceSegmentResponse;
import gal.subtitula.api.transparency.transcript.dto.SpeakerResponse;
import gal.subtitula.api.transparency.transcript.dto.TranscriptResponse;
import gal.subtitula.api.transparency.transcript.dto.TranscriptRevisionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class TranscriptQueryService {

    private final ProjectService projects;
    private final TranscriptRevisionRepository revisions;
    private final SpeakerRepository speakers;
    private final EvidenceSegmentRepository segments;
    private final LegacyTranscriptAdapter legacyAdapter;

    public TranscriptQueryService(
            ProjectService projects,
            TranscriptRevisionRepository revisions,
            SpeakerRepository speakers,
            EvidenceSegmentRepository segments,
            LegacyTranscriptAdapter legacyAdapter) {
        this.projects = projects;
        this.revisions = revisions;
        this.speakers = speakers;
        this.segments = segments;
        this.legacyAdapter = legacyAdapter;
    }

    @Transactional(readOnly = true)
    public TranscriptResponse get(UUID projectId, UUID userId) {
        Project project = projects.get(projectId, userId);
        return revisions.findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .map(revision -> normalized(project, revision))
            .orElseGet(() -> legacyAdapter.adapt(project));
    }

    private TranscriptResponse normalized(Project project, TranscriptRevision revision) {
        var revisionResponse = new TranscriptRevisionResponse(
            revision.getId(),
            revision.getVersionNumber(),
            lower(revision.getState()),
            lower(revision.getSource()),
            revision.getLanguageCode() == null ? project.getLanguage() : revision.getLanguageCode(),
            revision.getVersion());
        var speakerResponses = speakers.findByProjectIdOrderByProviderLabelAsc(project.getId())
            .stream()
            .map(speaker -> new SpeakerResponse(
                speaker.getId(),
                speaker.getProviderLabel(),
                speaker.getDisplayLabel(),
                speaker.getConfirmedName(),
                speaker.getRole(),
                lower(speaker.getIdentityState()),
                lower(speaker.getSource()),
                speaker.getVersion()))
            .toList();
        var segmentResponses = segments
            .findByTranscriptRevisionIdOrderBySequenceAsc(revision.getId())
            .stream()
            .map(segment -> new EvidenceSegmentResponse(
                segment.getId(),
                segment.getSequence(),
                segment.getStartMs(),
                segment.getEndMs(),
                segment.getSpeakerId(),
                segment.getReviewedText(),
                segment.getWordTimings(),
                lower(segment.getReviewState()),
                segment.getSignals(),
                segment.getVersion()))
            .toList();
        return new TranscriptResponse(revisionResponse, speakerResponses, segmentResponses, false);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
