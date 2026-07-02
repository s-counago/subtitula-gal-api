package gal.subtitula.api.font;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@Service
public class FontService {

    private final FontRepository fonts;

    public FontService(FontRepository fonts) { this.fonts = fonts; }

    @Transactional
    public Font upload(UUID userId, MultipartFile file, String family) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String ct = file.getContentType() == null ? "font/ttf" : file.getContentType();
        String fam = (family == null || family.isBlank()) ? file.getOriginalFilename() : family;
        return fonts.save(Font.create(userId, fam, ct, bytes));
    }

    @Transactional(readOnly = true)
    public List<Font> list(UUID userId) {
        return fonts.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Font getBytes(UUID id, UUID userId) {
        return fonts.findByIdAndUserId(id, userId).orElseThrow(FontNotFoundException::new);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Font f = fonts.findByIdAndUserId(id, userId).orElseThrow(FontNotFoundException::new);
        fonts.delete(f);
    }
}
