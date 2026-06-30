package gal.subtitula.api.auth.dto;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(@NotBlank String token,
                                   @NotBlank @Size(min = 8, max = 100) String password) {}
