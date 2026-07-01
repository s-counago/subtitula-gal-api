package gal.subtitula.api.token;

import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class OneTimeTokenService {

    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofHours(1);
    private static final SecureRandom RNG = new SecureRandom();

    private final VerificationTokenRepository verificationTokens;
    private final ResetTokenRepository resetTokens;
    private final UserRepository users;

    public OneTimeTokenService(VerificationTokenRepository v, ResetTokenRepository r, UserRepository u) {
        this.verificationTokens = v; this.resetTokens = r; this.users = u;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    @Transactional
    public String issueVerification(User user) {
        String raw = randomToken();
        verificationTokens.save(VerificationToken.of(user.getId(), hash(raw),
            Instant.now().plus(VERIFY_TTL)));
        return raw;
    }

    @Transactional
    public User consumeVerification(String raw) {
        VerificationToken t = verificationTokens.findByTokenHash(hash(raw))
            .orElseThrow(InvalidTokenException::new);
        if (Instant.now().isAfter(t.getExpiresAt())) throw new InvalidTokenException();
        if (verificationTokens.markUsedIfUnused(t.getId(), Instant.now()) == 0) throw new InvalidTokenException();
        return users.findById(t.getUserId()).orElseThrow(InvalidTokenException::new);
    }

    @Transactional
    public String issueReset(User user) {
        String raw = randomToken();
        resetTokens.save(ResetToken.of(user.getId(), hash(raw),
            Instant.now().plus(RESET_TTL)));
        return raw;
    }

    @Transactional
    public User consumeReset(String raw) {
        ResetToken t = resetTokens.findByTokenHash(hash(raw))
            .orElseThrow(InvalidTokenException::new);
        if (Instant.now().isAfter(t.getExpiresAt())) throw new InvalidTokenException();
        if (resetTokens.markUsedIfUnused(t.getId(), Instant.now()) == 0) throw new InvalidTokenException();
        return users.findById(t.getUserId()).orElseThrow(InvalidTokenException::new);
    }
}
