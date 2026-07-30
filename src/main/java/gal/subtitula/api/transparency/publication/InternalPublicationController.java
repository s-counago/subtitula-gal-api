package gal.subtitula.api.transparency.publication;

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

    public InternalPublicationController(PublicSessionQueryService service) {
        this.service = service;
    }

    @GetMapping("/{slug}/media")
    public PublicationMediaContext media(
            @PathVariable String slug,
            @RequestParam(required = false) Integer version) {
        return service.media(slug, version);
    }

    @GetMapping("/{slug}/versions/{version}/media")
    public PublicationMediaContext versionedMedia(
            @PathVariable String slug,
            @PathVariable Integer version) {
        return service.media(slug, version);
    }
}
