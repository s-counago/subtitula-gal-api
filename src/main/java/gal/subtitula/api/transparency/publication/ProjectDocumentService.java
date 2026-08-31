package gal.subtitula.api.transparency.publication;

import gal.subtitula.api.project.ProjectService;
import gal.subtitula.api.transparency.TransparencyStateConflictException;
import gal.subtitula.api.transparency.model.ProjectDocumentType;
import gal.subtitula.api.transparency.publication.dto.CreateOfficialDocumentRequest;
import gal.subtitula.api.transparency.publication.dto.ProjectDocumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProjectDocumentService {

    private final ProjectService projects;
    private final ProjectDocumentRepository documents;

    public ProjectDocumentService(
            ProjectService projects,
            ProjectDocumentRepository documents) {
        this.projects = projects;
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public List<ProjectDocumentResponse> list(UUID projectId, UUID userId) {
        projects.get(projectId, userId);
        return documents.findByProjectIdOrderByDocumentDateAscTitleAsc(projectId)
            .stream()
            .map(ProjectDocumentResponse::from)
            .toList();
    }

    @Transactional
    public ProjectDocumentResponse createOfficial(
            UUID projectId,
            UUID userId,
            CreateOfficialDocumentRequest request) {
        projects.get(projectId, userId);
        String url = validHttpsUrl(request.officialUrl());
        String title = required(request.title(), 500);
        ProjectDocumentType type = enumValue(request.type());
        ProjectDocument document = documents.saveAndFlush(ProjectDocument.official(
            projectId,
            type,
            url,
            title,
            bounded(request.issuingBody(), 500),
            request.documentDate(),
            request.publicationPermission()));
        return ProjectDocumentResponse.from(document);
    }

    private static String validHttpsUrl(String value) {
        String normalized = required(value, 4_000);
        try {
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw conflict("Official document URL must use HTTPS");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException ignored) {
            throw conflict("Official document URL is invalid");
        }
    }

    private static ProjectDocumentType enumValue(String value) {
        if (value == null || value.isBlank()) {
            return ProjectDocumentType.OTHER;
        }
        try {
            return ProjectDocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw conflict("Document type is invalid");
        }
    }

    private static String required(String value, int max) {
        String normalized = bounded(value, max);
        if (normalized == null) {
            throw conflict("Required document value is missing");
        }
        return normalized;
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > max) {
            throw conflict("Document value is too long");
        }
        return normalized;
    }

    private static TransparencyStateConflictException conflict(String message) {
        return new TransparencyStateConflictException(message);
    }
}
