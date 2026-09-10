package gal.subtitula.api.transparency.agenda;

import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.agenda.dto.AgendaAlignmentResponse;
import gal.subtitula.api.transparency.agenda.dto.AgendaResponse;
import gal.subtitula.api.transparency.agenda.dto.CheckAgendaAlignmentRequest;
import gal.subtitula.api.transparency.agenda.dto.ReplaceAgendaRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}")
public class AgendaController {

    private final AgendaService service;
    private final TransparencyCapabilities capabilities;

    public AgendaController(AgendaService service, TransparencyCapabilities capabilities) {
        this.service = service;
        this.capabilities = capabilities;
    }

    @GetMapping("/agenda")
    public AgendaResponse get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        capabilities.requireAutomaticAgenda();
        return service.get(projectId, principal.userId());
    }

    @PutMapping("/agenda")
    public AgendaResponse replace(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody ReplaceAgendaRequest request) {
        capabilities.requireAutomaticAgenda();
        return service.replace(projectId, principal.userId(), request);
    }

    @PatchMapping("/agenda-alignments/{alignmentId}")
    public AgendaAlignmentResponse check(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID alignmentId,
            @RequestBody CheckAgendaAlignmentRequest request) {
        capabilities.requireAutomaticAgenda();
        return service.check(projectId, alignmentId, principal.userId(), request);
    }
}
