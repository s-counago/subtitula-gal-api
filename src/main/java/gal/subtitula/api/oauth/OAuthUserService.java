package gal.subtitula.api.oauth;

import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthUserService {

    private static final String GOOGLE = "google";
    private final OAuthAccountRepository oauthAccounts;
    private final UserRepository users;

    public OAuthUserService(OAuthAccountRepository oauthAccounts, UserRepository users) {
        this.oauthAccounts = oauthAccounts;
        this.users = users;
    }

    @Transactional
    public User findOrCreate(String googleSub, String email, boolean emailVerified, String displayName) {
        // 1. Existing oauth link → return its user.
        var existingLink = oauthAccounts.findByProviderAndProviderUserId(GOOGLE, googleSub);
        if (existingLink.isPresent()) {
            return users.findById(existingLink.get().getUserId()).orElseThrow();
        }

        // 2. Look up by email.
        var byEmail = users.findByEmail(email.toLowerCase());
        if (byEmail.isPresent()) {
            User u = byEmail.get();
            if (emailVerified) {
                // Safe to link: Google has verified ownership of this email.
                oauthAccounts.save(OAuthAccount.of(u.getId(), GOOGLE, googleSub));
                if (!u.isEmailVerified()) {
                    u.setEmailVerified(true);
                    users.save(u);
                }
                return u;
            } else {
                // Unverified Google email conflicts with existing password account — reject.
                throw new EmailConflictException(email);
            }
        }

        // 3. No existing account: create a fresh user + oauth link.
        User created = User.create(email, displayName, null);
        created.setEmailVerified(emailVerified);
        users.save(created);
        oauthAccounts.save(OAuthAccount.of(created.getId(), GOOGLE, googleSub));
        return created;
    }
}
