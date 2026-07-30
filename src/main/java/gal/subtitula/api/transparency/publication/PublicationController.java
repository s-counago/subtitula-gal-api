package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.transparency.publication.dto.CreateCorrectionResponse;
import gal.subtitula.api.transparency.publication.dto.CreateOfficialDocumentRequest;
import gal.subtitula.api.transparency.publication.dto.CreatePublicationRequest;
import gal.subtitula.api.transparency.publication.dto.ProjectDocumentResponse;
import gal.subtitula.api.transparency.publication.dto.PublicationChecklistResponse;
import gal.subtitula.api.transparency.publication.dto.PublicationResponse;
import gal.subtitula.api.transparency.publication.dto.ReindexPublicationResponse;
import gal.subtitula.api.transparency.publication.dto.StartCorrectionRequest;
import gal.subtitula.api.transparency.publication.dto.WithdrawPublicationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}")
public class PublicationController {

    private final PublicationService publications;
    private final ProjectDocumentService documents;

    public PublicationController(
            PublicationService publications,
            ProjectDocumentService documents) {
        this.publications = publications;
        this.documents = documents;
    }

    @GetMapping("/publication-checklist")
    public PublicationChecklistResponse checklist(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        return publications.checklist(projectId, principal.userId());
    }

    @PostMapping("/publications")
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationResponse publish(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody CreatePublicationRequest request) {
        return publications.publish(projectId, principal.userId(), request);
    }

    @GetMapping("/publications/latest")
    public ResponseEntity<PublicationResponse> latest(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        PublicationResponse publication =
            publications.latest(projectId, principal.userId());
        return publication == null
            ? ResponseEntity.noContent().build()
            : ResponseEntity.ok(publication);
    }

    @PostMapping("/publications/{publicationId}/withdraw")
    public PublicationResponse withdraw(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID publicationId,
            @RequestBody WithdrawPublicationRequest request) {
        return publications.withdraw(
            projectId,
            publicationId,
            principal.userId(),
            request);
    }

    @PostMapping("/publications/{publicationId}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReindexPublicationResponse reindex(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID publicationId) {
        return publications.reindex(
            projectId,
            publicationId,
            principal.userId());
    }

    @PostMapping("/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateCorrectionResponse startCorrection(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody StartCorrectionRequest request) {
        return publications.startCorrection(projectId, principal.userId(), request);
    }

    @GetMapping("/documents")
    public List<ProjectDocumentResponse> documents(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        return documents.list(projectId, principal.userId());
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDocumentResponse createDocument(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody CreateOfficialDocumentRequest request) {
        return documents.createOfficial(projectId, principal.userId(), request);
    }
}
