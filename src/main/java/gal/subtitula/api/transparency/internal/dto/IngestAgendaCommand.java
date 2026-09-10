package gal.subtitula.api.transparency.internal.dto;

import java.util.List;
import java.util.UUID;

public record IngestAgendaCommand(
        UUID transcriptRevisionId,
        String contentHash,
        String rawArtifactKey,
        long expectedJobVersion,
        long expectedProjectVersion,
        List<IngestGuideCommand.Alignment> alignments) {
}
