package gal.subtitula.api.token;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class ResetToken {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "char(64)") private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;

    protected ResetToken() {}

    public static ResetToken of(UUID userId, String tokenHash, Instant expiresAt) {
        ResetToken t = new ResetToken();
        t.id = UUID.randomUUID();
        t.userId = userId; t.tokenHash = tokenHash; t.expiresAt = expiresAt;
        return t;
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
