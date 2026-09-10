package gal.subtitula.api.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.project.dto.ProjectResponse;
import gal.subtitula.api.project.dto.InstitutionalProjectCreateRequest;
import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService service;
    private final ObjectMapper mapper;
    private final TransparencyCapabilities capabilities;

    public ProjectController(
            ProjectService service,
            ObjectMapper mapper,
            TransparencyCapabilities capabilities) {
        this.service = service;
        this.mapper = mapper;
        this.capabilities = capabilities;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "name", required = false) String name,
                                  @RequestParam(value = "style", required = false) String styleJson,
                                  @RequestParam(value = "workflowMode", required = false) String workflowMode,
                                  @RequestParam(value = "language", required = false) String language)
            throws Exception {
        JsonNode style = (styleJson == null || styleJson.isBlank()) ? null : mapper.readTree(styleJson);
        return ProjectResponse.from(service.createFromUpload(principal.userId(), file, name, style, workflowMode, language));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createInstitutionalDraft(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody InstitutionalProjectCreateRequest request) {
        capabilities.requireDurableInstitutionalUpload();
        return ProjectResponse.from(service.createInstitutionalDraft(
            principal.userId(),
            request.name(),
            request.language(),
            request.sessionDate(),
            request.body(),
            request.location(),
            request.sessionType()));
    }

    @GetMapping
    public java.util.List<gal.subtitula.api.project.dto.ProjectSummary> list(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(principal.userId()).stream()
            .map(gal.subtitula.api.project.dto.ProjectSummary::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable java.util.UUID id) {
        return ProjectResponse.from(service.get(id, principal.userId()));
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                  @PathVariable java.util.UUID id,
                                  @RequestBody gal.subtitula.api.project.dto.ProjectUpdateRequest req) {
        return ProjectResponse.from(service.update(id, principal.userId(), req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable java.util.UUID id) {
        service.delete(id, principal.userId());
    }
}
