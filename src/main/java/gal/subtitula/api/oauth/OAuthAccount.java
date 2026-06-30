package gal.subtitula.api.oauth;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "oauth_accounts")
public class OAuthAccount {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false) private String provider;
    @Column(name = "provider_user_id", nullable = false) private String providerUserId;

    protected OAuthAccount() {}

    public static OAuthAccount of(UUID userId, String provider, String providerUserId) {
        OAuthAccount a = new OAuthAccount();
        a.id = UUID.randomUUID();
        a.userId = userId;
        a.provider = provider;
        a.providerUserId = providerUserId;
        return a;
    }

    public UUID getUserId() { return userId; }
}
