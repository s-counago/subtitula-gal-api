package gal.subtitula.api.transparency.internal.dto;

import java.util.UUID;

public record EnrichmentStartResponse(
        UUID jobId,
        UUID projectId,
        String state,
        String stage,
        long jobVersion,
        long projectVersion) {
}
