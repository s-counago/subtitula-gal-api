package gal.subtitula.api.transparency.guide;

import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.guide.dto.DecisionReviewRequest;
import gal.subtitula.api.transparency.guide.dto.GuideDecisionResponse;
import gal.subtitula.api.transparency.guide.dto.SessionGuideResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}")
public class GuideController {

    private final GuideService service;
    private final TransparencyCapabilities capabilities;

    public GuideController(GuideService service, TransparencyCapabilities capabilities) {
        this.service = service;
        this.capabilities = capabilities;
    }

    @GetMapping("/guide")
    public SessionGuideResponse get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        capabilities.requireStructuredGuide();
        return service.get(projectId, principal.userId());
    }

    @PatchMapping("/guide/decisions/{decisionId}")
    public GuideDecisionResponse reviewDecision(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID decisionId,
            @RequestBody DecisionReviewRequest request) {
        capabilities.requireStructuredGuide();
        return service.reviewDecision(
            projectId,
            decisionId,
            principal.userId(),
            request);
    }
}
