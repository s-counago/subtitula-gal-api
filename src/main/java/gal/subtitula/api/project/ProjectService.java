package gal.subtitula.api.project;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final TranscriptionClient transcription;
    private final String languageHint;

    public ProjectService(ProjectRepository projects, TranscriptionClient transcription,
                          @Value("${app.elevenlabs.language-hint:}") String languageHint) {
        this.projects = projects;
        this.transcription = transcription;
        this.languageHint = languageHint;
    }

    @Transactional
    public Project createFromUpload(UUID userId, MultipartFile file, String name, JsonNode style, String workflowMode) {
        byte[] media;
        try {
            media = file.getBytes();   // in-request only — never persisted
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TranscriptionResult result = transcription.transcribe(
            media, file.getOriginalFilename(), file.getContentType(),
            (languageHint == null || languageHint.isBlank()) ? null : languageHint);

        double durationSec = result.words().stream().mapToDouble(Word::end).max().orElse(0);
        String projectName = (name == null || name.isBlank())
            ? (file.getOriginalFilename() == null ? "Novo proxecto" : file.getOriginalFilename())
            : name;

        Project project = Project.create(userId, projectName, result.languageCode(),
            durationSec, result.words(), style, normalizeWorkflowMode(workflowMode));
        return projects.save(project);
        // media goes out of scope here — no bytes stored
    }

    @Transactional(readOnly = true)
    public java.util.List<Project> list(UUID userId) {
        return projects.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Project get(UUID id, UUID userId) {
        return projects.findByIdAndUserId(id, userId)
            .orElseThrow(ProjectNotFoundException::new);
    }

    @Transactional
    public Project update(UUID id, UUID userId, gal.subtitula.api.project.dto.ProjectUpdateRequest req) {
        Project p = get(id, userId);               // throws ProjectNotFoundException if not owner
        if (req.name() != null) p.setName(req.name());
        if (req.words() != null) p.setWords(req.words());
        if (req.style() != null) p.setStyle(req.style());
        if (req.speedFactor() != null) p.setSpeedFactor(req.speedFactor());
        if (req.baseBox() != null) p.setBaseBox(req.baseBox());
        if (req.segments() != null) p.setSegments(req.segments());
        return projects.save(p);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Project p = get(id, userId);               // 404 for non-owner
        projects.delete(p);
    }

    private static String normalizeWorkflowMode(String workflowMode) {
        return "institution".equals(workflowMode) ? "institution" : "creator";
    }
}
