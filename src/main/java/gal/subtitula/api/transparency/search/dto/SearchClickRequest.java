package gal.subtitula.api.transparency.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SearchClickRequest(
        @NotNull UUID queryEventId,
        @NotNull UUID searchDocumentId,
        @Min(1) @Max(500) int resultRank) {
}
