package gal.subtitula.api.transparency.review;

import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.review.dto.CompleteReviewRequest;
import gal.subtitula.api.transparency.review.dto.CompleteReviewResponse;
import gal.subtitula.api.transparency.review.dto.ResolveReviewIssueRequest;
import gal.subtitula.api.transparency.review.dto.ReviewActivityRequest;
import gal.subtitula.api.transparency.review.dto.ReviewIssueResponse;
import gal.subtitula.api.transparency.review.dto.ReviewQueueResponse;
import gal.subtitula.api.transparency.review.dto.ReviewSessionResponse;
import gal.subtitula.api.transparency.review.dto.SegmentReviewRequest;
import gal.subtitula.api.transparency.review.dto.SpeakerReviewRequest;
import gal.subtitula.api.transparency.transcript.dto.EvidenceSegmentResponse;
import gal.subtitula.api.transparency.transcript.dto.SpeakerResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}")
public class ReviewController {

    private final ReviewService service;
    private final TransparencyCapabilities capabilities;

    public ReviewController(ReviewService service, TransparencyCapabilities capabilities) {
        this.service = service;
        this.capabilities = capabilities;
    }

    @GetMapping("/review-issues")
    public ReviewQueueResponse queue(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        requireEnabled();
        return service.queue(projectId, principal.userId());
    }

    @PostMapping("/review/open")
    public ReviewSessionResponse open(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        requireEnabled();
        return service.open(projectId, principal.userId());
    }

    @PostMapping("/review/activity")
    public ReviewSessionResponse activity(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody ReviewActivityRequest request) {
        requireEnabled();
        return service.activity(
            projectId,
            principal.userId(),
            request.reviewSessionId());
    }

    @PatchMapping("/segments/{segmentId}")
    public EvidenceSegmentResponse reviewSegment(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID segmentId,
            @RequestBody SegmentReviewRequest request) {
        requireEnabled();
        return service.reviewSegment(
            projectId,
            segmentId,
            principal.userId(),
            request);
    }

    @PatchMapping("/speakers/{speakerId}")
    public SpeakerResponse reviewSpeaker(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID speakerId,
            @RequestBody SpeakerReviewRequest request) {
        requireEnabled();
        return service.reviewSpeaker(
            projectId,
            speakerId,
            principal.userId(),
            request);
    }

    @PostMapping("/review-issues/{issueId}/resolve")
    public ReviewIssueResponse resolve(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @RequestBody ResolveReviewIssueRequest request) {
        requireEnabled();
        return service.resolve(projectId, issueId, principal.userId(), request);
    }

    @PostMapping("/review/complete")
    public CompleteReviewResponse complete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody CompleteReviewRequest request) {
        requireEnabled();
        return service.complete(projectId, principal.userId(), request);
    }

    private void requireEnabled() {
        capabilities.requireExceptionReview();
    }
}
