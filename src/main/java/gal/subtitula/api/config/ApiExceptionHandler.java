package gal.subtitula.api.config;

import gal.subtitula.api.auth.EmailAlreadyRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<Map<String, String>> conflict(EmailAlreadyRegisteredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "email_already_registered",
                         "message", "That email is already registered — log in instead."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "validation_failed",
                         "message", "Check the submitted fields."));
    }

    @ExceptionHandler(gal.subtitula.api.auth.InvalidCredentialsException.class)
    ResponseEntity<Map<String, String>> invalidCreds() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "invalid_credentials",
                         "message", "Invalid email or password."));
    }

    @ExceptionHandler(gal.subtitula.api.token.InvalidTokenException.class)
    ResponseEntity<Map<String, String>> invalidToken() {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "invalid_token",
                         "message", "This link is invalid or has expired."));
    }

    @ExceptionHandler(gal.subtitula.api.project.ProjectNotFoundException.class)
    ResponseEntity<Map<String, String>> projectNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "not_found", "message", "Project not found."));
    }
}
