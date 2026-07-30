package gal.subtitula.api.transparency.publication.dto;

import java.time.LocalDate;

public record CreateOfficialDocumentRequest(
        String type,
        String officialUrl,
        String title,
        String issuingBody,
        LocalDate documentDate,
        boolean publicationPermission) {
}
