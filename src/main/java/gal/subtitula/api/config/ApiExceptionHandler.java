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

    @ExceptionHandler(gal.subtitula.api.project.ProjectApprovedException.class)
    ResponseEntity<Map<String, String>> projectApproved() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", "project_approved",
                         "message", "This session is approved — create a new version to change it."));
    }

    @ExceptionHandler(gal.subtitula.api.font.FontNotFoundException.class)
    ResponseEntity<Map<String, String>> fontNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "not_found", "message", "Font not found."));
    }

    @ExceptionHandler(gal.subtitula.api.transparency.review.ReviewConflictException.class)
    ResponseEntity<Map<String, String>> reviewConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of(
                "error", "review_conflict",
                "message", "Refresh the review state and try again."));
    }

    @ExceptionHandler(gal.subtitula.api.transparency.TransparencyStateConflictException.class)
    ResponseEntity<Map<String, String>> transparencyConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of(
                "error", "transparency_conflict",
                "message", "Refresh the session state and try again."));
    }

    @ExceptionHandler(gal.subtitula.api.transparency.search.SearchRequestException.class)
    ResponseEntity<Map<String, String>> invalidSearch(
            gal.subtitula.api.transparency.search.SearchRequestException exception) {
        return ResponseEntity.badRequest()
            .body(Map.of(
                "error", "invalid_search",
                "message", exception.getMessage()));
    }

    @ExceptionHandler(gal.subtitula.api.transparency.search.SearchUnavailableException.class)
    ResponseEntity<Map<String, String>> searchUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "search_unavailable",
                "message", "Public search is temporarily unavailable."));
    }

    @ExceptionHandler(gal.subtitula.api.transparency.capability.CapabilityDisabledException.class)
    ResponseEntity<Map<String, String>> capabilityDisabled(
            gal.subtitula.api.transparency.capability.CapabilityDisabledException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of(
                "error", "capability_disabled",
                "capability", exception.capability(),
                "message", "This capability is not enabled in this environment."));
    }
}
