package gal.subtitula.api.transparency.agenda.dto;

public record AgendaItemInput(
        String externalIdentifier,
        String title,
        String description,
        String source,
        String visibility) {
}
