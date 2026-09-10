package gal.subtitula.api.transparency.processing;

import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.processing.dto.ProcessingStatusResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/processing")
public class ProcessingController {

    private final ProcessingQueryService service;
    private final TransparencyCapabilities capabilities;

    public ProcessingController(
            ProcessingQueryService service,
            TransparencyCapabilities capabilities) {
        this.service = service;
        this.capabilities = capabilities;
    }

    @GetMapping
    public ProcessingStatusResponse get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        capabilities.requireProcessingStatus();
        return service.get(projectId, principal.userId());
    }
}
