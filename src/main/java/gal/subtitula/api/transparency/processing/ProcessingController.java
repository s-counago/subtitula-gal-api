package gal.subtitula.api.transparency.processing;

import gal.subtitula.api.auth.AuthPrincipal;
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

    public ProcessingController(ProcessingQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ProcessingStatusResponse get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        return service.get(projectId, principal.userId());
    }
}
