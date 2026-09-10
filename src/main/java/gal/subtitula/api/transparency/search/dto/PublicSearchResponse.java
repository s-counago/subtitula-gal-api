package gal.subtitula.api.transparency.search.dto;

import java.util.List;
import java.util.UUID;

public record PublicSearchResponse(
        String query,
        String mode,
        long total,
        int limit,
        int offset,
        UUID queryEventId,
        List<PublicSearchResult> results) {
}
