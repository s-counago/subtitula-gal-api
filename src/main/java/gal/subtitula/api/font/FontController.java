package gal.subtitula.api.font;

import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.font.dto.FontResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/fonts")
public class FontController {

    private final FontService service;

    public FontController(FontService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FontResponse upload(@AuthenticationPrincipal AuthPrincipal principal,
                               @RequestParam("file") MultipartFile file,
                               @RequestParam(value = "family", required = false) String family) {
        return FontResponse.from(service.upload(principal.userId(), file, family));
    }

    @GetMapping
    public List<FontResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.list(principal.userId()).stream().map(FontResponse::from).toList();
    }

    @GetMapping("/{id}")
    public org.springframework.http.ResponseEntity<byte[]> serve(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable java.util.UUID id) {
        Font f = service.getBytes(id, principal.userId());
        return org.springframework.http.ResponseEntity.ok()
            .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, f.getContentType())
            .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
            .body(f.getBytes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable java.util.UUID id) {
        service.delete(id, principal.userId());
    }
}
