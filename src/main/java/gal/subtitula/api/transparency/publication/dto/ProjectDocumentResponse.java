package gal.subtitula.api.transparency.publication.dto;

import gal.subtitula.api.transparency.publication.ProjectDocument;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public record ProjectDocumentResponse(
        UUID id,
        String type,
        String officialUrl,
        String title,
        String issuingBody,
        LocalDate documentDate,
        String visibility,
        boolean publicationPermission,
        long version) {

    public static ProjectDocumentResponse from(ProjectDocument value) {
        return new ProjectDocumentResponse(
            value.getId(),
            value.getType().name().toLowerCase(Locale.ROOT),
            value.getOfficialUrl(),
            value.getTitle(),
            value.getIssuingBody(),
            value.getDocumentDate(),
            value.getVisibility().name().toLowerCase(Locale.ROOT),
            value.hasPublicationPermission(),
            value.getVersion());
    }
}
