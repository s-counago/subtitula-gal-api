package gal.subtitula.api.transparency.search;

import java.util.UUID;

public record PublicationReadyForIndexEvent(
        UUID publicationId,
        UUID processingJobId) {
}
