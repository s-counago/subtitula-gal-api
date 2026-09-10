package gal.subtitula.api.transparency.internal;

import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.internal.dto.HybridSearchCommand;
import gal.subtitula.api.transparency.search.PublicSearchQuery;
import gal.subtitula.api.transparency.search.PublicSearchService;
import gal.subtitula.api.transparency.search.dto.PublicSearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/processing")
public class InternalHybridSearchController {

    private final PublicSearchService search;
    private final TransparencyCapabilities capabilities;

    public InternalHybridSearchController(
            PublicSearchService search,
            TransparencyCapabilities capabilities) {
        this.search = search;
        this.capabilities = capabilities;
    }

    @PostMapping("/search/hybrid")
    public PublicSearchResponse search(@RequestBody HybridSearchCommand command) {
        capabilities.requireHybridSearch();
        return search.hybrid(
            new PublicSearchQuery(
                command.query(),
                command.publicSlug(),
                command.organizationId(),
                command.sessionBody(),
                command.dateFrom(),
                command.dateTo(),
                command.speakerId(),
                command.agendaItemId(),
                command.language(),
                command.kind(),
                command.limit(),
                command.offset()),
            command.embedding());
    }
}
