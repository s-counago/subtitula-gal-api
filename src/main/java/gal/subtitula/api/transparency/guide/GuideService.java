package gal.subtitula.api.transparency.guide;

import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.TransparencyStateConflictException;
import gal.subtitula.api.transparency.agenda.AgendaAlignmentRepository;
import gal.subtitula.api.transparency.guide.dto.DecisionReviewRequest;
import gal.subtitula.api.transparency.guide.dto.GuideContributionResponse;
import gal.subtitula.api.transparency.guide.dto.GuideDecisionResponse;
import gal.subtitula.api.transparency.guide.dto.GuideEvidenceResponse;
import gal.subtitula.api.transparency.guide.dto.GuideTopicResponse;
import gal.subtitula.api.transparency.guide.dto.SessionGuideResponse;
import gal.subtitula.api.transparency.model.DecisionStatus;
import gal.subtitula.api.transparency.model.GuideEvidenceSubjectType;
import gal.subtitula.api.transparency.transcript.EvidenceSegment;
import gal.subtitula.api.transparency.transcript.EvidenceSegmentRepository;
import gal.subtitula.api.transparency.transcript.Speaker;
import gal.subtitula.api.transparency.transcript.SpeakerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GuideService {

    private final ProjectService projects;
    private final SessionGuideRepository guides;
    private final GuideTopicRepository topics;
    private final GuideContributionRepository contributions;
    private final GuideDecisionRepository decisions;
    private final GuideEvidenceLinkRepository evidenceLinks;
    private final EvidenceSegmentRepository segments;
    private final SpeakerRepository speakers;
    private final AgendaAlignmentRepository alignments;

    public GuideService(
            ProjectService projects,
            SessionGuideRepository guides,
            GuideTopicRepository topics,
            GuideContributionRepository contributions,
            GuideDecisionRepository decisions,
            GuideEvidenceLinkRepository evidenceLinks,
            EvidenceSegmentRepository segments,
            SpeakerRepository speakers,
            AgendaAlignmentRepository alignments) {
        this.projects = projects;
        this.guides = guides;
        this.topics = topics;
        this.contributions = contributions;
        this.decisions = decisions;
        this.evidenceLinks = evidenceLinks;
        this.segments = segments;
        this.speakers = speakers;
        this.alignments = alignments;
    }

    @Transactional(readOnly = true)
    public SessionGuideResponse get(UUID projectId, UUID userId) {
        projects.get(projectId, userId);
        return response(latest(projectId));
    }

    @Transactional
    public GuideDecisionResponse reviewDecision(
            UUID projectId,
            UUID decisionId,
            UUID userId,
            DecisionReviewRequest request) {
        projects.get(projectId, userId);
        SessionGuide guide = latest(projectId);
        List<GuideTopic> guideTopics = topics.findByGuideIdOrderByOrdinalAsc(guide.getId());
        List<UUID> topicIds = guideTopics.stream().map(GuideTopic::getId).toList();
        GuideDecision decision = decisions.findByIdAndTopicIdIn(decisionId, topicIds)
            .orElseThrow(() -> conflict("Decision candidate not found"));
        if (decision.getVersion() != request.expectedVersion()) {
            throw conflict("Stale decision candidate");
        }
        String action = request.action() == null
            ? ""
            : request.action().trim().toLowerCase(Locale.ROOT);
        if ("confirm".equals(action)) {
            decision.confirm(userId, bounded(request.neutralDescription(), 2_000));
        } else if ("omit".equals(action)) {
            decision.omit(userId);
        } else {
            throw conflict("Unsupported decision action");
        }
        decisions.flush();
        return decisionResponse(
            decision,
            evidenceBySubject(guide, GuideEvidenceSubjectType.DECISION),
            segmentMap(guide),
            speakerMap(projectId));
    }

    private SessionGuideResponse response(SessionGuide guide) {
        List<GuideTopic> guideTopics = topics.findByGuideIdOrderByOrdinalAsc(guide.getId());
        List<UUID> topicIds = guideTopics.stream().map(GuideTopic::getId).toList();
        List<GuideContribution> guideContributions = topicIds.isEmpty()
            ? List.of()
            : contributions.findByTopicIdInOrderByOrdinalAsc(topicIds);
        List<GuideDecision> guideDecisions = topicIds.isEmpty()
            ? List.of()
            : decisions.findByTopicIdInOrderByOrdinalAsc(topicIds);
        Map<UUID, List<GuideContribution>> contributionsByTopic =
            guideContributions.stream().collect(Collectors.groupingBy(
                GuideContribution::getTopicId));
        Map<UUID, List<GuideDecision>> decisionsByTopic =
            guideDecisions.stream().collect(Collectors.groupingBy(
                GuideDecision::getTopicId));
        Map<UUID, EvidenceSegment> bySegment = segmentMap(guide);
        Map<UUID, Speaker> bySpeaker = speakerMap(guide.getProjectId());
        Map<UUID, List<GuideEvidenceLink>> topicEvidence =
            evidenceBySubject(guide, GuideEvidenceSubjectType.TOPIC);
        Map<UUID, List<GuideEvidenceLink>> contributionEvidence =
            evidenceBySubject(guide, GuideEvidenceSubjectType.CONTRIBUTION);
        Map<UUID, List<GuideEvidenceLink>> decisionEvidence =
            evidenceBySubject(guide, GuideEvidenceSubjectType.DECISION);

        List<GuideTopicResponse> responses = guideTopics.stream()
            .map(topic -> {
                EvidenceSegment start = requiredSegment(bySegment, topic.getStartSegmentId());
                EvidenceSegment end = requiredSegment(bySegment, topic.getEndSegmentId());
                List<GuideContributionResponse> contributionResponses =
                    contributionsByTopic.getOrDefault(topic.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(GuideContribution::getOrdinal))
                        .map(value -> contributionResponse(
                            value, contributionEvidence, bySegment, bySpeaker))
                        .toList();
                List<GuideDecisionResponse> decisionResponses =
                    decisionsByTopic.getOrDefault(topic.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(GuideDecision::getOrdinal))
                        .map(value -> decisionResponse(
                            value, decisionEvidence, bySegment, bySpeaker))
                        .toList();
                return new GuideTopicResponse(
                    topic.getId(),
                    topic.getOrdinal(),
                    topic.getTitle(),
                    topic.getNeutralSummary(),
                    topic.getAliases(),
                    topic.getAgendaItemId(),
                    topic.getStartSegmentId(),
                    topic.getEndSegmentId(),
                    start.getStartMs(),
                    end.getEndMs(),
                    lower(topic.getGenerationState()),
                    citations(topicEvidence.get(topic.getId()), bySegment, bySpeaker),
                    contributionResponses,
                    decisionResponses,
                    topic.getVersion());
            })
            .toList();
        int candidates = (int) guideDecisions.stream()
            .filter(value -> value.getStatus() == DecisionStatus.CANDIDATE)
            .count();
        return new SessionGuideResponse(
            guide.getId(),
            guide.getProjectId(),
            guide.getTranscriptRevisionId(),
            guide.getVersionNumber(),
            guide.getSchemaVersion(),
            lower(guide.getState()),
            "Resumo asistido con fontes",
            guide.getCreatedAt(),
            (int) alignments.countByTranscriptRevisionIdAndRequiresHumanCheckTrue(
                guide.getTranscriptRevisionId()),
            candidates,
            responses,
            guide.getVersion());
    }

    private GuideContributionResponse contributionResponse(
            GuideContribution value,
            Map<UUID, List<GuideEvidenceLink>> evidence,
            Map<UUID, EvidenceSegment> bySegment,
            Map<UUID, Speaker> bySpeaker) {
        Speaker speaker = bySpeaker.get(value.getSpeakerId());
        return new GuideContributionResponse(
            value.getId(),
            value.getSpeakerId(),
            speaker == null ? "Persoa non identificada" : speaker.getDisplayLabel(),
            lower(value.getKind()),
            value.getNeutralSummary(),
            lower(value.getGenerationState()),
            citations(evidence.get(value.getId()), bySegment, bySpeaker),
            value.getVersion());
    }

    private GuideDecisionResponse decisionResponse(
            GuideDecision value,
            Map<UUID, List<GuideEvidenceLink>> evidence,
            Map<UUID, EvidenceSegment> bySegment,
            Map<UUID, Speaker> bySpeaker) {
        return new GuideDecisionResponse(
            value.getId(),
            value.getAgendaItemId(),
            value.getNeutralDescription(),
            lower(value.getStatus()),
            value.getMotion(),
            value.getResult(),
            value.getVoteDetails(),
            citations(evidence.get(value.getId()), bySegment, bySpeaker),
            value.getVersion());
    }

    private List<GuideEvidenceResponse> citations(
            List<GuideEvidenceLink> links,
            Map<UUID, EvidenceSegment> bySegment,
            Map<UUID, Speaker> bySpeaker) {
        if (links == null || links.isEmpty()) {
            throw conflict("Guide object has no evidence");
        }
        return links.stream()
            .sorted(Comparator.comparingInt(GuideEvidenceLink::getOrdinal))
            .map(link -> {
                EvidenceSegment segment = requiredSegment(
                    bySegment,
                    link.getEvidenceSegmentId());
                Speaker speaker = bySpeaker.get(segment.getSpeakerId());
                return new GuideEvidenceResponse(
                    segment.getId(),
                    segment.getStartMs(),
                    segment.getEndMs(),
                    segment.getSpeakerId(),
                    speaker == null
                        ? "Persoa non identificada"
                        : speaker.getDisplayLabel(),
                    segment.getReviewedText(),
                    link.getPurpose().toLowerCase(Locale.ROOT));
            })
            .toList();
    }

    private Map<UUID, List<GuideEvidenceLink>> evidenceBySubject(
            SessionGuide guide,
            GuideEvidenceSubjectType type) {
        return evidenceLinks.findByGuideIdOrderBySubjectTypeAscSubjectIdAscOrdinalAsc(
                guide.getId())
            .stream()
            .filter(value -> value.getSubjectType() == type)
            .collect(Collectors.groupingBy(GuideEvidenceLink::getSubjectId));
    }

    private Map<UUID, EvidenceSegment> segmentMap(SessionGuide guide) {
        return segments
            .findByTranscriptRevisionIdOrderBySequenceAsc(guide.getTranscriptRevisionId())
            .stream()
            .collect(Collectors.toMap(EvidenceSegment::getId, Function.identity()));
    }

    private Map<UUID, Speaker> speakerMap(UUID projectId) {
        return speakers.findByProjectIdOrderByProviderLabelAsc(projectId)
            .stream()
            .collect(Collectors.toMap(Speaker::getId, Function.identity()));
    }

    private SessionGuide latest(UUID projectId) {
        return guides.findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .orElseThrow(() -> conflict("Structured guide is not ready"));
    }

    private static EvidenceSegment requiredSegment(
            Map<UUID, EvidenceSegment> byId,
            UUID id) {
        EvidenceSegment value = byId.get(id);
        if (value == null) {
            throw conflict("Guide evidence does not belong to its transcript");
        }
        return value;
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw conflict("Guide value is too long");
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static TransparencyStateConflictException conflict(String message) {
        return new TransparencyStateConflictException(message);
    }
}
