package gal.subtitula.api.auth.dto;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(@Email @NotBlank String email) {}
