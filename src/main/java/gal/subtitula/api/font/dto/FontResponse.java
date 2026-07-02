package gal.subtitula.api.font.dto;

import gal.subtitula.api.font.Font;
import java.time.Instant;
import java.util.UUID;

public record FontResponse(UUID id, String family, String contentType, long sizeBytes, Instant createdAt) {
    public static FontResponse from(Font f) {
        return new FontResponse(f.getId(), f.getFamily(), f.getContentType(), f.getSizeBytes(), f.getCreatedAt());
    }
}
