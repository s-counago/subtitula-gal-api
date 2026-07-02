package gal.subtitula.api.font;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "custom_fonts")
public class Font {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String family;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] bytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Font() {}

    public static Font create(UUID userId, String family, String contentType, byte[] bytes) {
        Font f = new Font();
        f.id = UUID.randomUUID();
        f.userId = userId;
        f.family = family;
        f.contentType = contentType;
        f.bytes = bytes;
        f.sizeBytes = bytes.length;
        f.createdAt = Instant.now();
        return f;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getFamily() { return family; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getBytes() { return bytes; }
    public Instant getCreatedAt() { return createdAt; }
}
