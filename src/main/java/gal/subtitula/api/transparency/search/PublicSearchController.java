package gal.subtitula.api.transparency.search;

import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.search.dto.PublicSearchResponse;
import gal.subtitula.api.transparency.search.dto.SearchClickRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/public")
public class PublicSearchController {

    private final PublicSearchService search;
    private final TransparencyCapabilities capabilities;

    public PublicSearchController(
            PublicSearchService search,
            TransparencyCapabilities capabilities) {
        this.search = search;
        this.capabilities = capabilities;
    }

    @GetMapping("/search")
    public PublicSearchResponse global(
            @RequestParam("q") String query,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String body,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID speakerId,
            @RequestParam(required = false) UUID agendaItemId,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        capabilities.requireLexicalSearch();
        return search.search(query(
            query,
            null,
            organizationId,
            body,
            dateFrom,
            dateTo,
            speakerId,
            agendaItemId,
            language,
            kind,
            limit,
            offset));
    }

    @GetMapping("/sessions/{slug}/search")
    public PublicSearchResponse session(
            @PathVariable String slug,
            @RequestParam("q") String query,
            @RequestParam(required = false) UUID speakerId,
            @RequestParam(required = false) UUID agendaItemId,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        capabilities.requireLexicalSearch();
        return search.search(query(
            query,
            slug,
            null,
            null,
            null,
            null,
            speakerId,
            agendaItemId,
            null,
            kind,
            limit,
            offset));
    }

    @PostMapping("/search/clicks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void click(@Valid @RequestBody SearchClickRequest request) {
        capabilities.requireLexicalSearch();
        search.recordClick(request);
    }

    private static PublicSearchQuery query(
            String query,
            String slug,
            UUID organizationId,
            String body,
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID speakerId,
            UUID agendaItemId,
            String language,
            String kind,
            int limit,
            int offset) {
        return new PublicSearchQuery(
            query,
            slug,
            organizationId,
            body,
            dateFrom,
            dateTo,
            speakerId,
            agendaItemId,
            language,
            kind,
            limit,
            offset);
    }
}
