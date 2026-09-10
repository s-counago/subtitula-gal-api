package gal.subtitula.api.transparency.internal.dto;

public record IngestAgendaResponse(
        int alignmentCount,
        int optionalCheckCount,
        boolean duplicate) {
}
