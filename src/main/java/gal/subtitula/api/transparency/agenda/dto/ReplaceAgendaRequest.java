package gal.subtitula.api.transparency.agenda.dto;

import java.util.List;

public record ReplaceAgendaRequest(
        List<AgendaItemInput> items,
        String pastedText) {
}
