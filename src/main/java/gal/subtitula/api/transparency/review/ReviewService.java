package gal.subtitula.api.transparency.review;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectRepository;
import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.lifecycle.InstitutionalProjectStatus;
import gal.subtitula.api.transparency.lifecycle.ProcessingJobType;
import gal.subtitula.api.transparency.lifecycle.ProcessingStage;
import gal.subtitula.api.transparency.model.ReviewIssueSeverity;
import gal.subtitula.api.transparency.model.ReviewIssueState;
import gal.subtitula.api.transparency.model.TranscriptEditKind;
import gal.subtitula.api.transparency.processing.ProcessingJob;
import gal.subtitula.api.transparency.processing.ProcessingJobRepository;
import gal.subtitula.api.transparency.review.dto.CompleteReviewRequest;
import gal.subtitula.api.transparency.review.dto.CompleteReviewResponse;
import gal.subtitula.api.transparency.review.dto.ResolveReviewIssueRequest;
import gal.subtitula.api.transparency.review.dto.ReviewIssueResponse;
import gal.subtitula.api.transparency.review.dto.ReviewQueueResponse;
import gal.subtitula.api.transparency.review.dto.ReviewSessionResponse;
import gal.subtitula.api.transparency.review.dto.SegmentReviewRequest;
import gal.subtitula.api.transparency.review.dto.SpeakerReviewRequest;
import gal.subtitula.api.transparency.transcript.EvidenceSegment;
import gal.subtitula.api.transparency.transcript.EvidenceSegmentRepository;
import gal.subtitula.api.transparency.transcript.Speaker;
import gal.subtitula.api.transparency.transcript.SpeakerRepository;
import gal.subtitula.api.transparency.transcript.TranscriptEditEvent;
import gal.subtitula.api.transparency.transcript.TranscriptEditEventRepository;
import gal.subtitula.api.transparency.transcript.TranscriptRevision;
import gal.subtitula.api.transparency.transcript.TranscriptRevisionRepository;
import gal.subtitula.api.transparency.transcript.dto.EvidenceSegmentResponse;
import gal.subtitula.api.transparency.transcript.dto.SpeakerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private static final int MAX_SEGMENT_TEXT = 20_000;
    private static final int MAX_NAME = 255;

    private final ProjectService projectService;
    private final ProjectRepository projects;
    private final TranscriptRevisionRepository revisions;
    private final EvidenceSegmentRepository segments;
    private final SpeakerRepository speakers;
    private final ReviewIssueRepository issues;
    private final ReviewSessionRepository sessions;
    private final TranscriptEditEventRepository editEvents;
    private final ProcessingJobRepository jobs;
    private final TransparencyCapabilities capabilities;

    public ReviewService(
            ProjectService projectService,
            ProjectRepository projects,
            TranscriptRevisionRepository revisions,
            EvidenceSegmentRepository segments,
            SpeakerRepository speakers,
            ReviewIssueRepository issues,
            ReviewSessionRepository sessions,
            TranscriptEditEventRepository editEvents,
            ProcessingJobRepository jobs,
            TransparencyCapabilities capabilities) {
        this.projectService = projectService;
        this.projects = projects;
        this.revisions = revisions;
        this.segments = segments;
        this.speakers = speakers;
        this.issues = issues;
        this.sessions = sessions;
        this.editEvents = editEvents;
        this.jobs = jobs;
        this.capabilities = capabilities;
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse queue(UUID projectId, UUID userId) {
        Project project = owner(projectId, userId);
        TranscriptRevision revision = latestRevision(projectId);
        Map<UUID, EvidenceSegment> bySegment = segments
            .findByTranscriptRevisionIdOrderBySequenceAsc(revision.getId())
            .stream()
            .collect(Collectors.toMap(EvidenceSegment::getId, Function.identity()));
        Map<UUID, Speaker> bySpeaker = speakers
            .findByProjectIdOrderByProviderLabelAsc(projectId)
            .stream()
            .collect(Collectors.toMap(Speaker::getId, Function.identity()));
        var all = issues.findByProjectIdOrderByCreatedAtAsc(projectId);
        Map<String, Integer> openByType = new LinkedHashMap<>();
        int openRequired = 0;
        int openWarnings = 0;
        int resolved = 0;
        int dismissed = 0;
        for (ReviewIssue issue : all) {
            if (issue.getState() == ReviewIssueState.OPEN) {
                openByType.merge(lower(issue.getType()), 1, Integer::sum);
                if (issue.getSeverity() == ReviewIssueSeverity.REQUIRED) {
                    openRequired++;
                } else {
                    openWarnings++;
                }
            } else if (issue.getState() == ReviewIssueState.RESOLVED) {
                resolved++;
            } else {
                dismissed++;
            }
        }
        var responses = all.stream()
            .sorted(Comparator
                .comparing((ReviewIssue value) -> value.getState() != ReviewIssueState.OPEN)
                .thenComparing(value -> value.getSeverity() != ReviewIssueSeverity.REQUIRED)
                .thenComparing(ReviewIssue::getType)
                .thenComparing(ReviewIssue::getId))
            .map(issue -> response(issue, bySegment, bySpeaker))
            .toList();
        return new ReviewQueueResponse(
            projectId,
            revision.getId(),
            revision.getVersion(),
            project.getVersion(),
            openRequired,
            openWarnings,
            resolved,
            dismissed,
            openByType,
            responses);
    }

    @Transactional
    public ReviewSessionResponse open(UUID projectId, UUID userId) {
        Project project = owner(projectId, userId);
        if (project.getStatus() != InstitutionalProjectStatus.REVIEW_REQUIRED) {
            throw conflict("Project is not waiting for transcript review");
        }
        TranscriptRevision revision = latestRevision(projectId);
        ReviewSession session = sessions
            .findByProjectIdAndReviewerIdAndCompletedAtIsNull(projectId, userId)
            .orElse(null);
        if (session != null) {
            if (!session.getTranscriptRevisionId().equals(revision.getId())) {
                throw conflict("Active review belongs to another revision");
            }
            session.recordActivity(Instant.now());
            sessions.flush();
            return ReviewSessionResponse.from(session);
        }
        return ReviewSessionResponse.from(
            sessions.saveAndFlush(ReviewSession.open(projectId, revision.getId(), userId)));
    }

    @Transactional
    public ReviewSessionResponse activity(
            UUID projectId,
            UUID userId,
            UUID sessionId) {
        owner(projectId, userId);
        ReviewSession session = session(projectId, userId, sessionId);
        session.recordActivity(Instant.now());
        sessions.flush();
        return ReviewSessionResponse.from(session);
    }

    @Transactional
    public EvidenceSegmentResponse reviewSegment(
            UUID projectId,
            UUID segmentId,
            UUID userId,
            SegmentReviewRequest request) {
        owner(projectId, userId);
        TranscriptRevision revision = workingRevision(projectId);
        EvidenceSegment segment = segments
            .findByIdAndTranscriptRevisionId(segmentId, revision.getId())
            .orElseThrow(() -> conflict("Evidence segment not found"));
        checkVersion(segment.getVersion(), request.expectedVersion(), "segment");
        String text = boundedRequired(request.text(), MAX_SEGMENT_TEXT);
        UUID speakerId = request.speakerId() == null
            ? segment.getSpeakerId()
            : speaker(projectId, request.speakerId()).getId();
        UUID priorSpeakerId = segment.getSpeakerId();
        String priorText = segment.getReviewedText();
        String prior = segmentCanonical(segment.getReviewedText(), segment.getSpeakerId());
        segment.review(text, speakerId);
        String next = segmentCanonical(segment.getReviewedText(), segment.getSpeakerId());
        editEvents.save(TranscriptEditEvent.create(
            revision.getId(),
            segment.getId(),
            userId,
            !java.util.Objects.equals(priorSpeakerId, speakerId)
                && priorText.equals(segment.getReviewedText())
                    ? TranscriptEditKind.SPEAKER
                    : TranscriptEditKind.TEXT,
            sha256(prior),
            sha256(next)));
        session(projectId, userId, request.reviewSessionId())
            .recordManualEdit(Instant.now());
        segments.flush();
        editEvents.flush();
        sessions.flush();
        return segmentResponse(segment);
    }

    @Transactional
    public SpeakerResponse reviewSpeaker(
            UUID projectId,
            UUID speakerId,
            UUID userId,
            SpeakerReviewRequest request) {
        owner(projectId, userId);
        TranscriptRevision revision = workingRevision(projectId);
        Speaker speaker = speaker(projectId, speakerId);
        checkVersion(speaker.getVersion(), request.expectedVersion(), "speaker");
        String prior = speakerCanonical(speaker);
        speaker.reviewIdentity(
            bounded(request.confirmedName(), MAX_NAME),
            bounded(request.role(), MAX_NAME));
        String next = speakerCanonical(speaker);
        editEvents.save(TranscriptEditEvent.create(
            revision.getId(),
            null,
            userId,
            TranscriptEditKind.SPEAKER,
            sha256(prior),
            sha256(next)));
        session(projectId, userId, request.reviewSessionId())
            .recordManualEdit(Instant.now());
        speakers.flush();
        editEvents.flush();
        sessions.flush();
        return speakerResponse(speaker);
    }

    @Transactional
    public ReviewIssueResponse resolve(
            UUID projectId,
            UUID issueId,
            UUID userId,
            ResolveReviewIssueRequest request) {
        owner(projectId, userId);
        ReviewIssue issue = issues.findByIdAndProjectId(issueId, projectId)
            .orElseThrow(() -> conflict("Review issue not found"));
        checkVersion(issue.getVersion(), request.expectedVersion(), "review issue");
        ReviewSession session = session(projectId, userId, request.reviewSessionId());
        if (issue.getState() == ReviewIssueState.OPEN) {
            String resolution = boundedRequired(request.resolution(), 80);
            boolean dismissed = "dismissed".equalsIgnoreCase(resolution);
            if (dismissed) {
                issue.dismiss("dismissed", userId);
            } else {
                issue.resolve(resolution.toLowerCase(Locale.ROOT), userId);
            }
            session.recordResolution(dismissed, Instant.now());
        }
        issues.flush();
        sessions.flush();
        Map<UUID, EvidenceSegment> bySegment = new HashMap<>();
        if (issue.getSegmentId() != null) {
            segments.findById(issue.getSegmentId())
                .ifPresent(value -> bySegment.put(value.getId(), value));
        }
        Map<UUID, Speaker> bySpeaker = new HashMap<>();
        if (issue.getSpeakerId() != null) {
            speakers.findById(issue.getSpeakerId())
                .ifPresent(value -> bySpeaker.put(value.getId(), value));
        }
        return response(issue, bySegment, bySpeaker);
    }

    @Transactional
    public CompleteReviewResponse complete(
            UUID projectId,
            UUID userId,
            CompleteReviewRequest request) {
        Project project = owner(projectId, userId);
        TranscriptRevision revision = workingRevision(projectId);
        checkVersion(project.getVersion(), request.expectedProjectVersion(), "project");
        checkVersion(revision.getVersion(), request.expectedRevisionVersion(), "revision");
        long required = issues.countByProjectIdAndStateAndSeverity(
            projectId,
            ReviewIssueState.OPEN,
            ReviewIssueSeverity.REQUIRED);
        if (required > 0) {
            throw conflict("Required review issues remain open");
        }
        ReviewSession session = session(projectId, userId, request.reviewSessionId());
        String reviewedContentHash = reviewedContentHash(projectId, revision.getId());
        revision.freeze(userId, reviewedContentHash);
        project.transitionTo(InstitutionalProjectStatus.ENRICHING);
        UUID enrichmentJobId = null;
        if (!capabilities.structuredGuide()) {
            project.transitionTo(InstitutionalProjectStatus.READY);
        } else {
            String idempotencyKey = "enrich:" + revision.getId() + ":"
                + revision.getContentHash();
            ProcessingJob enrichmentJob = jobs.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> jobs.save(ProcessingJob.queued(
                    projectId,
                    ProcessingJobType.ENRICH,
                    idempotencyKey,
                    ProcessingStage.QUEUED)));
            enrichmentJob.configure(
                revision.getContentHash(),
                revision.getRawArtifactKey(),
                null,
                null,
                null);
            enrichmentJobId = enrichmentJob.getId();
        }
        session.complete(Instant.now());
        revisions.save(revision);
        projects.save(project);
        sessions.save(session);
        jobs.flush();
        revisions.flush();
        projects.flush();
        sessions.flush();
        return new CompleteReviewResponse(
            projectId,
            revision.getId(),
            enrichmentJobId,
            lower(project.getStatus()),
            session.getActiveDurationMs(),
            session.getResolvedCount(),
            session.getDismissedCount(),
            session.getManualEditCount());
    }

    private ReviewIssueResponse response(
            ReviewIssue issue,
            Map<UUID, EvidenceSegment> bySegment,
            Map<UUID, Speaker> bySpeaker) {
        EvidenceSegment segment = bySegment.get(issue.getSegmentId());
        Speaker speaker = bySpeaker.get(issue.getSpeakerId());
        if (speaker == null && segment != null && segment.getSpeakerId() != null) {
            speaker = bySpeaker.get(segment.getSpeakerId());
        }
        return new ReviewIssueResponse(
            issue.getId(),
            lower(issue.getType()),
            lower(issue.getSeverity()),
            lower(issue.getState()),
            issue.getResolution(),
            issue.getSegmentId(),
            issue.getSpeakerId(),
            segment == null ? null : segment.getStartMs(),
            segment == null ? null : segment.getEndMs(),
            segment == null ? null : segment.getReviewedText(),
            speaker == null ? null : speaker.getDisplayLabel(),
            issue.getSignals(),
            issue.getVersion());
    }

    private Project owner(UUID projectId, UUID userId) {
        return projectService.get(projectId, userId);
    }

    private TranscriptRevision latestRevision(UUID projectId) {
        return revisions.findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .orElseThrow(() -> conflict("Normalized transcript not found"));
    }

    private TranscriptRevision workingRevision(UUID projectId) {
        TranscriptRevision revision = latestRevision(projectId);
        if (!revision.getState().name().equals("WORKING")) {
            throw conflict("Transcript revision is not editable");
        }
        return revision;
    }

    private Speaker speaker(UUID projectId, UUID speakerId) {
        return speakers.findByIdAndProjectId(speakerId, projectId)
            .orElseThrow(() -> conflict("Speaker not found"));
    }

    private ReviewSession session(
            UUID projectId,
            UUID userId,
            UUID sessionId) {
        if (sessionId == null) {
            throw conflict("Review session is required");
        }
        return sessions.findByIdAndProjectIdAndReviewerId(
                sessionId,
                projectId,
                userId)
            .filter(value -> value.getCompletedAt() == null)
            .orElseThrow(() -> conflict("Active review session not found"));
    }

    private static EvidenceSegmentResponse segmentResponse(EvidenceSegment segment) {
        return new EvidenceSegmentResponse(
            segment.getId(),
            segment.getSequence(),
            segment.getStartMs(),
            segment.getEndMs(),
            segment.getSpeakerId(),
            segment.getReviewedText(),
            segment.getWordTimings(),
            lower(segment.getReviewState()),
            segment.getSignals(),
            segment.getVersion());
    }

    private static SpeakerResponse speakerResponse(Speaker speaker) {
        return new SpeakerResponse(
            speaker.getId(),
            speaker.getProviderLabel(),
            speaker.getDisplayLabel(),
            speaker.getConfirmedName(),
            speaker.getRole(),
            lower(speaker.getIdentityState()),
            lower(speaker.getSource()),
            speaker.getVersion());
    }

    private static String segmentCanonical(String text, UUID speakerId) {
        return text + "\n" + (speakerId == null ? "" : speakerId);
    }

    private String reviewedContentHash(UUID projectId, UUID revisionId) {
        StringBuilder canonical = new StringBuilder();
        segments.findByTranscriptRevisionIdOrderBySequenceAsc(revisionId)
            .forEach(value -> canonical
                .append(value.getSequence()).append('\u001f')
                .append(value.getStartMs()).append('\u001f')
                .append(value.getEndMs()).append('\u001f')
                .append(value.getSpeakerId()).append('\u001f')
                .append(value.getReviewedText()).append('\u001e'));
        speakers.findByProjectIdOrderByProviderLabelAsc(projectId)
            .forEach(value -> canonical
                .append(value.getId()).append('\u001f')
                .append(value.getDisplayLabel()).append('\u001f')
                .append(value.getRole()).append('\u001e'));
        return sha256(canonical.toString());
    }

    private static String speakerCanonical(Speaker speaker) {
        return String.join(
            "\n",
            value(speaker.getDisplayLabel()),
            value(speaker.getConfirmedName()),
            value(speaker.getRole()),
            lower(speaker.getIdentityState()));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String boundedRequired(String value, int maximum) {
        if (value == null || value.isBlank()) {
            throw conflict("Required review value is missing");
        }
        return bounded(value.trim(), maximum);
    }

    private static String bounded(String value, int maximum) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximum
            ? trimmed
            : trimmed.substring(0, maximum);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static void checkVersion(long actual, long expected, String resource) {
        if (actual != expected) {
            throw conflict("Stale " + resource + " version");
        }
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static ReviewConflictException conflict(String message) {
        return new ReviewConflictException(message);
    }
}
