package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.transparency.publication.dto.PublicSessionResponse;
import gal.subtitula.api.transparency.publication.dto.PublicSessionSummary;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/public/sessions")
public class PublicSessionController {

    private final PublicSessionQueryService service;

    public PublicSessionController(PublicSessionQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PublicSessionSummary>> archive() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
            .body(service.archive());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PublicSessionResponse> get(
            @PathVariable String slug,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
            .body(service.get(slug, version));
    }
}
