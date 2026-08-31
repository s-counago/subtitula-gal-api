package gal.subtitula.api.transparency.transcript;

import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.transcript.dto.TranscriptResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/transcript")
public class TranscriptController {

    private final TranscriptQueryService service;
    private final TransparencyCapabilities capabilities;

    public TranscriptController(
            TranscriptQueryService service,
            TransparencyCapabilities capabilities) {
        this.service = service;
        this.capabilities = capabilities;
    }

    @GetMapping
    public TranscriptResponse get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        capabilities.requireNormalizedTranscript();
        return service.get(projectId, principal.userId());
    }
}
