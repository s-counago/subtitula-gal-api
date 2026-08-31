package gal.subtitula.api.transparency.publication.dto;

public record PublicationChecklistItem(
        String key,
        String label,
        boolean required,
        boolean satisfied,
        String detail) {
}
