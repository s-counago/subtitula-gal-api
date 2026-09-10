package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.publication.dto.PublicationMediaContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/processing/publications")
public class InternalPublicationController {

    private final PublicSessionQueryService service;
    private final TransparencyCapabilities capabilities;

    public InternalPublicationController(
            PublicSessionQueryService service,
            TransparencyCapabilities capabilities) {
        this.service = service;
        this.capabilities = capabilities;
    }

    @GetMapping("/{slug}/media")
    public PublicationMediaContext media(
            @PathVariable String slug,
            @RequestParam(required = false) Integer version) {
        capabilities.requirePublicPublication();
        return service.media(slug, version);
    }

    @GetMapping("/{slug}/versions/{version}/media")
    public PublicationMediaContext versionedMedia(
            @PathVariable String slug,
            @PathVariable Integer version) {
        capabilities.requirePublicPublication();
        return service.media(slug, version);
    }
}
