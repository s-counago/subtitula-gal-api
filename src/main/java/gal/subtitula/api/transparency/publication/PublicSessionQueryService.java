package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.transparency.agenda.AgendaAlignment;
import gal.subtitula.api.transparency.agenda.AgendaAlignmentRepository;
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
import gal.subtitula.api.transparency.guide.SessionGuideRepository;
import gal.subtitula.api.transparency.model.AgendaVisibility;
import gal.subtitula.api.transparency.model.DecisionStatus;
import gal.subtitula.api.transparency.model.GuideEvidenceSubjectType;
import gal.subtitula.api.transparency.model.PublicationState;
import gal.subtitula.api.transparency.model.RecordingUploadState;
import gal.subtitula.api.transparency.model.RecordingVisibility;
import gal.subtitula.api.transparency.publication.dto.PublicSessionResponse;
import gal.subtitula.api.transparency.publication.dto.PublicSessionSummary;
import gal.subtitula.api.transparency.publication.dto.PublicationMediaContext;
import gal.subtitula.api.transparency.recording.Recording;
import gal.subtitula.api.transparency.recording.RecordingRepository;
import gal.subtitula.api.transparency.transcript.EvidenceSegment;
import gal.subtitula.api.transparency.transcript.EvidenceSegmentRepository;
import gal.subtitula.api.transparency.transcript.Speaker;
import gal.subtitula.api.transparency.transcript.SpeakerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PublicSessionQueryService {

    private final PublicationRepository publications;
    private final RecordingRepository recordings;
    private final EvidenceSegmentRepository segments;
    private final SpeakerRepository speakers;
    private final AgendaItemRepository agendaItems;
    private final AgendaAlignmentRepository agendaAlignments;
    private final SessionGuideRepository guides;
    private final GuideTopicRepository topics;
    private final GuideContributionRepository contributions;
    private final GuideDecisionRepository decisions;
    private final GuideEvidenceLinkRepository evidenceLinks;
    private final PublicationDocumentRepository publicationDocuments;
    private final ProjectDocumentRepository documents;

    public PublicSessionQueryService(
            PublicationRepository publications,
            RecordingRepository recordings,
            EvidenceSegmentRepository segments,
            SpeakerRepository speakers,
            AgendaItemRepository agendaItems,
            AgendaAlignmentRepository agendaAlignments,
            SessionGuideRepository guides,
            GuideTopicRepository topics,
            GuideContributionRepository contributions,
            GuideDecisionRepository decisions,
            GuideEvidenceLinkRepository evidenceLinks,
            PublicationDocumentRepository publicationDocuments,
            ProjectDocumentRepository documents) {
        this.publications = publications;
        this.recordings = recordings;
        this.segments = segments;
        this.speakers = speakers;
        this.agendaItems = agendaItems;
        this.agendaAlignments = agendaAlignments;
        this.guides = guides;
        this.topics = topics;
        this.contributions = contributions;
        this.decisions = decisions;
        this.evidenceLinks = evidenceLinks;
        this.publicationDocuments = publicationDocuments;
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public PublicSessionResponse get(String slug, Integer version) {
        Publication publication = publication(slug, version);
        List<EvidenceSegment> transcript = segments
            .findByTranscriptRevisionIdOrderBySequenceAsc(
                publication.getTranscriptRevisionId());
        Map<UUID, EvidenceSegment> bySegment = transcript.stream()
            .collect(Collectors.toMap(EvidenceSegment::getId, Function.identity()));
        Map<UUID, Speaker> bySpeaker = speakers
            .findByProjectIdOrderByProviderLabelAsc(publication.getProjectId())
            .stream()
            .collect(Collectors.toMap(Speaker::getId, Function.identity()));
        List<PublicSessionResponse.TranscriptSegment> transcriptResponse = transcript
            .stream()
            .map(segment -> {
                Speaker speaker = bySpeaker.get(segment.getSpeakerId());
                return new PublicSessionResponse.TranscriptSegment(
                    segment.getId(),
                    segment.getSequence(),
                    segment.getStartMs(),
                    segment.getEndMs(),
                    segment.getSpeakerId(),
                    speakerLabel(speaker),
                    speaker == null ? null : speaker.getRole(),
                    segment.getReviewedText());
            })
            .toList();
        return new PublicSessionResponse(
            publication.getId(),
            publication.getPublicSlug(),
            publication.getVersionNumber(),
            publication.getTitle(),
            publication.getLanguageCode(),
            publication.getSessionDate(),
            publication.getSessionBody(),
            publication.getLocation(),
            publication.getSessionType(),
            publication.getCorrectionNote(),
            publication.getPublishedAt(),
            "/processing/publications/" + publication.getPublicSlug()
                + "/media?version=" + publication.getVersionNumber(),
            transcriptResponse,
            agenda(publication, bySegment),
            guide(publication, bySegment, bySpeaker),
            documents(publication));
    }

    @Transactional(readOnly = true)
    public List<PublicSessionSummary> archive() {
        return publications
            .findByStateOrderBySessionDateDescPublishedAtDesc(PublicationState.PUBLISHED)
            .stream()
            .map(value -> new PublicSessionSummary(
                value.getPublicSlug(),
                value.getVersionNumber(),
                value.getTitle(),
                value.getSessionDate(),
                value.getSessionBody(),
                value.getSessionType(),
                value.getPublishedAt()))
            .toList();
    }

    @Transactional(readOnly = true)
    public PublicationMediaContext media(String slug, Integer version) {
        Publication publication = publication(slug, version);
        Recording recording = recordings.findById(publication.getRecordingId())
            .filter(value -> value.getProjectId().equals(publication.getProjectId()))
            .filter(value -> value.getUploadState() == RecordingUploadState.VERIFIED)
            .filter(value -> value.getVisibility() == RecordingVisibility.PUBLIC)
            .orElseThrow(PublicSessionNotFoundException::new);
        return new PublicationMediaContext(
            recording.getObjectKey(),
            recording.getMimeType(),
            recording.getSizeBytes(),
            recording.getEtag());
    }

    private List<PublicSessionResponse.AgendaItem> agenda(
            Publication publication,
            Map<UUID, EvidenceSegment> bySegment) {
        Map<UUID, List<AgendaAlignment>> alignments = agendaAlignments
            .findByTranscriptRevisionIdOrderByOccurrenceAsc(
                publication.getTranscriptRevisionId())
            .stream()
            .collect(Collectors.groupingBy(AgendaAlignment::getAgendaItemId));
        return agendaItems.findByProjectIdOrderByOrdinalAsc(publication.getProjectId())
            .stream()
            .filter(value -> value.getVisibility() == AgendaVisibility.PUBLIC)
            .map(value -> new PublicSessionResponse.AgendaItem(
                value.getId(),
                value.getOrdinal(),
                value.getExternalIdentifier(),
                value.getTitle(),
                value.getDescription(),
                alignments.getOrDefault(value.getId(), List.of()).stream()
                    .map(alignment -> {
                        EvidenceSegment start = requiredSegment(
                            bySegment, alignment.getStartSegmentId());
                        EvidenceSegment end = requiredSegment(
                            bySegment, alignment.getEndSegmentId());
                        return new PublicSessionResponse.AgendaOccurrence(
                            start.getStartMs(),
                            end.getEndMs(),
                            alignment.isRevisited());
                    })
                    .toList()))
            .toList();
    }

    private PublicSessionResponse.Guide guide(
            Publication publication,
            Map<UUID, EvidenceSegment> bySegment,
            Map<UUID, Speaker> bySpeaker) {
        if (publication.getGuideId() == null) {
            return null;
        }
        var guide = guides.findByIdAndProjectId(
                publication.getGuideId(),
                publication.getProjectId())
            .filter(value -> value.getTranscriptRevisionId()
                .equals(publication.getTranscriptRevisionId()))
            .orElseThrow(() -> new IllegalStateException("Pinned guide is invalid"));
        List<GuideTopic> guideTopics = topics
            .findByGuideIdOrderByOrdinalAsc(guide.getId());
        List<UUID> topicIds = guideTopics.stream().map(GuideTopic::getId).toList();
        List<GuideContribution> guideContributions = topicIds.isEmpty()
            ? List.of()
            : contributions.findByTopicIdInOrderByOrdinalAsc(topicIds);
        List<GuideDecision> guideDecisions = topicIds.isEmpty()
            ? List.of()
            : decisions.findByTopicIdInOrderByOrdinalAsc(topicIds);
        Map<UUID, List<GuideContribution>> contributionMap =
            guideContributions.stream().collect(Collectors.groupingBy(
                GuideContribution::getTopicId));
        Map<UUID, List<GuideDecision>> decisionMap =
            guideDecisions.stream().collect(Collectors.groupingBy(
                GuideDecision::getTopicId));
        Map<UUID, List<GuideEvidenceLink>> topicEvidence =
            evidence(guide.getId(), GuideEvidenceSubjectType.TOPIC);
        Map<UUID, List<GuideEvidenceLink>> contributionEvidence =
            evidence(guide.getId(), GuideEvidenceSubjectType.CONTRIBUTION);
        Map<UUID, List<GuideEvidenceLink>> decisionEvidence =
            evidence(guide.getId(), GuideEvidenceSubjectType.DECISION);
        List<PublicSessionResponse.Topic> topicResponses = guideTopics.stream()
            .map(topic -> {
                EvidenceSegment start = requiredSegment(
                    bySegment, topic.getStartSegmentId());
                EvidenceSegment end = requiredSegment(
                    bySegment, topic.getEndSegmentId());
                return new PublicSessionResponse.Topic(
                    topic.getId(),
                    topic.getTitle(),
                    topic.getNeutralSummary(),
                    topic.getAliases(),
                    topic.getAgendaItemId(),
                    start.getStartMs(),
                    end.getEndMs(),
                    citations(topicEvidence.get(topic.getId()), bySegment, bySpeaker),
                    contributionMap.getOrDefault(topic.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(GuideContribution::getOrdinal))
                        .map(value -> new PublicSessionResponse.Contribution(
                            value.getId(),
                            value.getSpeakerId(),
                            speakerLabel(bySpeaker.get(value.getSpeakerId())),
                            value.getKind().name().toLowerCase(Locale.ROOT),
                            value.getNeutralSummary(),
                            citations(
                                contributionEvidence.get(value.getId()),
                                bySegment,
                                bySpeaker)))
                        .toList(),
                    decisionMap.getOrDefault(topic.getId(), List.of()).stream()
                        .filter(value -> value.getStatus() == DecisionStatus.CONFIRMED
                            || value.getStatus() == DecisionStatus.DOCUMENT_SUPPORTED)
                        .sorted(Comparator.comparingInt(GuideDecision::getOrdinal))
                        .map(value -> new PublicSessionResponse.Decision(
                            value.getId(),
                            value.getNeutralDescription(),
                            value.getStatus().name().toLowerCase(Locale.ROOT),
                            value.getMotion(),
                            value.getResult(),
                            citations(
                                decisionEvidence.get(value.getId()),
                                bySegment,
                                bySpeaker)))
                        .toList());
            })
            .toList();
        return new PublicSessionResponse.Guide(
            "Resumo asistido con fontes",
            topicResponses);
    }

    private List<PublicSessionResponse.Document> documents(Publication publication) {
        List<UUID> ids = publicationDocuments
            .findByPublicationIdOrderByOrdinalAsc(publication.getId())
            .stream()
            .map(PublicationDocument::getDocumentId)
            .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, ProjectDocument> byId = documents.findAllById(ids).stream()
            .collect(Collectors.toMap(ProjectDocument::getId, Function.identity()));
        return ids.stream()
            .map(byId::get)
            .filter(java.util.Objects::nonNull)
            .map(value -> new PublicSessionResponse.Document(
                value.getId(),
                value.getType().name().toLowerCase(Locale.ROOT),
                value.getTitle(),
                value.getOfficialUrl(),
                value.getIssuingBody(),
                value.getDocumentDate()))
            .toList();
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

    private List<PublicSessionResponse.Evidence> citations(
            List<GuideEvidenceLink> links,
            Map<UUID, EvidenceSegment> bySegment,
            Map<UUID, Speaker> bySpeaker) {
        if (links == null || links.isEmpty()) {
            throw new IllegalStateException("Published guide object has no evidence");
        }
        return links.stream()
            .sorted(Comparator.comparingInt(GuideEvidenceLink::getOrdinal))
            .map(link -> {
                EvidenceSegment segment = requiredSegment(
                    bySegment, link.getEvidenceSegmentId());
                return new PublicSessionResponse.Evidence(
                    segment.getId(),
                    segment.getStartMs(),
                    segment.getEndMs(),
                    speakerLabel(bySpeaker.get(segment.getSpeakerId())),
                    segment.getReviewedText());
            })
            .toList();
    }

    private Publication publication(String rawSlug, Integer version) {
        String slug = normalizedSlug(rawSlug);
        if (version == null) {
            return publications
                .findByPublicSlugAndState(slug, PublicationState.PUBLISHED)
                .orElseThrow(PublicSessionNotFoundException::new);
        }
        if (version <= 0) {
            throw new PublicSessionNotFoundException();
        }
        return publications.findByPublicSlugAndVersionNumber(slug, version)
            .filter(value -> value.getState() == PublicationState.PUBLISHED
                || value.getState() == PublicationState.SUPERSEDED)
            .orElseThrow(PublicSessionNotFoundException::new);
    }

    private static String normalizedSlug(String value) {
        if (value == null
                || value.length() > 160
                || !value.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            throw new PublicSessionNotFoundException();
        }
        return value;
    }

    private static EvidenceSegment requiredSegment(
            Map<UUID, EvidenceSegment> bySegment,
            UUID segmentId) {
        EvidenceSegment value = bySegment.get(segmentId);
        if (value == null) {
            throw new IllegalStateException("Published evidence is invalid");
        }
        return value;
    }

    private static String speakerLabel(Speaker speaker) {
        return speaker == null
            ? "Persoa non identificada"
            : speaker.getDisplayLabel();
    }
}
