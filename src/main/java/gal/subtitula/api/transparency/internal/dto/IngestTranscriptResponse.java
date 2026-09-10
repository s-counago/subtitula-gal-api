package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record IngestTranscriptResponse(
        UUID revisionId,
        int segmentCount,
        int requiredIssueCount,
        boolean duplicate) {
}
