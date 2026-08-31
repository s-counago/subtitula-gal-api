package gal.subtitula.api.transparency.agenda;

import gal.subtitula.api.project.Project;
import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.TransparencyStateConflictException;
import gal.subtitula.api.transparency.agenda.dto.AgendaAlignmentResponse;
import gal.subtitula.api.transparency.agenda.dto.AgendaItemInput;
import gal.subtitula.api.transparency.agenda.dto.AgendaItemResponse;
import gal.subtitula.api.transparency.agenda.dto.AgendaResponse;
import gal.subtitula.api.transparency.agenda.dto.CheckAgendaAlignmentRequest;
import gal.subtitula.api.transparency.agenda.dto.ReplaceAgendaRequest;
import gal.subtitula.api.transparency.lifecycle.InstitutionalProjectStatus;
import gal.subtitula.api.transparency.model.AgendaSource;
import gal.subtitula.api.transparency.model.AgendaVisibility;
import gal.subtitula.api.transparency.transcript.EvidenceSegment;
import gal.subtitula.api.transparency.transcript.EvidenceSegmentRepository;
import gal.subtitula.api.transparency.transcript.TranscriptRevision;
import gal.subtitula.api.transparency.transcript.TranscriptRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AgendaService {

    private static final int MAX_ITEMS = 200;
    private static final Pattern LIST_PREFIX = Pattern.compile(
        "^\\s*(?:(?:\\d+|[IVXLCDM]+)[.)-]|[-*•])\\s*",
        Pattern.CASE_INSENSITIVE);
    private static final EnumSet<InstitutionalProjectStatus> EDITABLE_STATES = EnumSet.of(
        InstitutionalProjectStatus.DRAFT,
        InstitutionalProjectStatus.UPLOADING,
        InstitutionalProjectStatus.UPLOADED,
        InstitutionalProjectStatus.TRANSCRIBING,
        InstitutionalProjectStatus.REVIEW_REQUIRED);

    private final ProjectService projects;
    private final AgendaItemRepository items;
    private final AgendaAlignmentRepository alignments;
    private final TranscriptRevisionRepository revisions;
    private final EvidenceSegmentRepository segments;

    public AgendaService(
            ProjectService projects,
            AgendaItemRepository items,
            AgendaAlignmentRepository alignments,
            TranscriptRevisionRepository revisions,
            EvidenceSegmentRepository segments) {
        this.projects = projects;
        this.items = items;
        this.alignments = alignments;
        this.revisions = revisions;
        this.segments = segments;
    }

    @Transactional(readOnly = true)
    public AgendaResponse get(UUID projectId, UUID userId) {
        projects.get(projectId, userId);
        return response(projectId);
    }

    @Transactional
    public AgendaResponse replace(
            UUID projectId,
            UUID userId,
            ReplaceAgendaRequest request) {
        Project project = projects.get(projectId, userId);
        if (!"institution".equals(project.getWorkflowMode())
                || !EDITABLE_STATES.contains(project.getStatus())) {
            throw conflict("Agenda can only be changed before enrichment starts");
        }
        List<AgendaItemInput> input = normalizedInput(request);
        if (input.size() > MAX_ITEMS) {
            throw conflict("Agenda has too many items");
        }
        items.deleteByProjectId(projectId);
        items.flush();
        int ordinal = 0;
        for (AgendaItemInput value : input) {
            String title = required(value.title(), 500);
            items.save(AgendaItem.create(
                projectId,
                ordinal++,
                bounded(value.externalIdentifier(), 120),
                title,
                bounded(value.description(), 20_000),
                enumValue(AgendaSource.class, value.source(), AgendaSource.PASTE),
                enumValue(AgendaVisibility.class, value.visibility(), AgendaVisibility.PUBLIC)));
        }
        items.flush();
        return response(projectId);
    }

    @Transactional
    public AgendaAlignmentResponse check(
            UUID projectId,
            UUID alignmentId,
            UUID userId,
            CheckAgendaAlignmentRequest request) {
        projects.get(projectId, userId);
        TranscriptRevision revision = revisions
            .findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .orElseThrow(() -> conflict("Transcript not found"));
        AgendaAlignment value = alignments
            .findByIdAndTranscriptRevisionId(alignmentId, revision.getId())
            .orElseThrow(() -> conflict("Agenda alignment not found"));
        if (value.getVersion() != request.expectedVersion()) {
            throw conflict("Stale agenda alignment");
        }
        String action = request.action() == null
            ? ""
            : request.action().trim().toLowerCase(Locale.ROOT);
        if ("confirm".equals(action)) {
            value.confirm(userId);
        } else if ("adjust".equals(action)) {
            EvidenceSegment start = segment(revision.getId(), request.startSegmentId());
            EvidenceSegment end = segment(revision.getId(), request.endSegmentId());
            if (start.getSequence() > end.getSequence()) {
                throw conflict("Agenda range is reversed");
            }
            value.adjust(start.getId(), end.getId(), userId);
        } else {
            throw conflict("Unsupported agenda check action");
        }
        alignments.flush();
        return alignmentResponse(value, segmentMap(revision.getId()));
    }

    private AgendaResponse response(UUID projectId) {
        List<AgendaItemResponse> itemResponses = items
            .findByProjectIdOrderByOrdinalAsc(projectId)
            .stream()
            .map(AgendaItemResponse::from)
            .toList();
        TranscriptRevision revision = revisions
            .findFirstByProjectIdOrderByVersionNumberDesc(projectId)
            .orElse(null);
        if (revision == null) {
            return new AgendaResponse(projectId, null, itemResponses, List.of(), 0);
        }
        Map<UUID, EvidenceSegment> byId = segmentMap(revision.getId());
        List<AgendaAlignmentResponse> alignmentResponses = alignments
            .findByTranscriptRevisionIdOrderByOccurrenceAsc(revision.getId())
            .stream()
            .sorted(Comparator.comparingInt(value ->
                byId.get(value.getStartSegmentId()).getSequence()))
            .map(value -> alignmentResponse(value, byId))
            .toList();
        int checks = (int) alignmentResponses.stream()
            .filter(AgendaAlignmentResponse::requiresHumanCheck)
            .count();
        return new AgendaResponse(
            projectId,
            revision.getId(),
            itemResponses,
            alignmentResponses,
            checks);
    }

    private List<AgendaItemInput> normalizedInput(ReplaceAgendaRequest request) {
        if (request != null && request.items() != null && !request.items().isEmpty()) {
            return request.items();
        }
        if (request == null || request.pastedText() == null) {
            return List.of();
        }
        List<AgendaItemInput> values = new ArrayList<>();
        request.pastedText().lines()
            .map(LIST_PREFIX::matcher)
            .map(matcher -> matcher.replaceFirst("").trim())
            .filter(value -> !value.isBlank())
            .forEach(value -> values.add(new AgendaItemInput(
                null, value, null, "paste", "public")));
        return values;
    }

    private Map<UUID, EvidenceSegment> segmentMap(UUID revisionId) {
        Map<UUID, EvidenceSegment> byId = new HashMap<>();
        segments.findByTranscriptRevisionIdOrderBySequenceAsc(revisionId)
            .forEach(value -> byId.put(value.getId(), value));
        return byId;
    }

    private EvidenceSegment segment(UUID revisionId, UUID segmentId) {
        if (segmentId == null) {
            throw conflict("Agenda segment is required");
        }
        return segments.findByIdAndTranscriptRevisionId(segmentId, revisionId)
            .orElseThrow(() -> conflict("Agenda segment does not belong to the transcript"));
    }

    private static AgendaAlignmentResponse alignmentResponse(
            AgendaAlignment value,
            Map<UUID, EvidenceSegment> byId) {
        EvidenceSegment start = byId.get(value.getStartSegmentId());
        EvidenceSegment end = byId.get(value.getEndSegmentId());
        if (start == null || end == null) {
            throw conflict("Agenda evidence is missing");
        }
        return new AgendaAlignmentResponse(
            value.getId(),
            value.getAgendaItemId(),
            value.getOccurrence(),
            start.getId(),
            end.getId(),
            start.getStartMs(),
            end.getEndMs(),
            value.getState().name().toLowerCase(Locale.ROOT),
            value.isRequiresHumanCheck(),
            value.getAlgorithmVersion(),
            value.isRevisited(),
            value.getVersion());
    }

    private static String required(String value, int max) {
        String normalized = bounded(value, max);
        if (normalized == null || normalized.isBlank()) {
            throw conflict("Agenda item title is required");
        }
        return normalized;
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw conflict("Agenda value is too long");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type,
            String value,
            T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw conflict("Unsupported agenda enum value");
        }
    }

    private static TransparencyStateConflictException conflict(String message) {
        return new TransparencyStateConflictException(message);
    }
}
