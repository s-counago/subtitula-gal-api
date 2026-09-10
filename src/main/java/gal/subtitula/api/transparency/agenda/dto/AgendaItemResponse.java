package gal.subtitula.api.transparency.agenda.dto;

import gal.subtitula.api.transparency.agenda.AgendaItem;

import java.util.Locale;
import java.util.UUID;

public record AgendaItemResponse(
        UUID id,
        int ordinal,
        String externalIdentifier,
        String title,
        String description,
        String source,
        String visibility,
        long version) {

    public static AgendaItemResponse from(AgendaItem value) {
        return new AgendaItemResponse(
            value.getId(),
            value.getOrdinal(),
            value.getExternalIdentifier(),
            value.getTitle(),
            value.getDescription(),
            value.getSource().name().toLowerCase(Locale.ROOT),
            value.getVisibility().name().toLowerCase(Locale.ROOT),
            value.getVersion());
    }
}
