package gal.subtitula.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record InstitutionalProjectCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 20) String language,
        LocalDate sessionDate,
        @Size(max = 255) String body,
        @Size(max = 255) String location,
        @Size(max = 40) String sessionType) {
}
