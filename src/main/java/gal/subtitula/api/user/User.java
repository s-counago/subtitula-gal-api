package gal.subtitula.api.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_hash")
    private String passwordHash;          // nullable: Google-only users

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public static User create(String email, String displayName, String passwordHash) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.email = email.toLowerCase();
        u.displayName = displayName;
        u.passwordHash = passwordHash;
        u.emailVerified = false;
        u.createdAt = Instant.now();
        u.updatedAt = u.createdAt;
        return u;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean v) { this.emailVerified = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String h) { this.passwordHash = h; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String n) { this.displayName = n; }
}
