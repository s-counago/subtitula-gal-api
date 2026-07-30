package gal.subtitula.api.transparency.internal.dto;

import java.time.Instant;

public record ExpiredUploadCandidatesCommand(
        Instant expiredBefore,
        Integer limit) {
}
