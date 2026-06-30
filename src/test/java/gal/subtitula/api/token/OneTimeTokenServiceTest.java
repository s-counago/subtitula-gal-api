package gal.subtitula.api.token;

import gal.subtitula.api.support.AbstractIntegrationTest;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class OneTimeTokenServiceTest extends AbstractIntegrationTest {

    @Autowired OneTimeTokenService tokens;
    @Autowired UserRepository users;
    @Autowired VerificationTokenRepository verificationTokens;

    @Test
    void issueThenConsumeReturnsUser_andIsSingleUse() {
        User u = users.save(User.create("tok@example.com", "Tok", "h"));
        String raw = tokens.issueVerification(u);

        User consumed = tokens.consumeVerification(raw);
        assertEquals(u.getId(), consumed.getId());

        // Second consume of the same token fails (single-use).
        assertThrows(InvalidTokenException.class, () -> tokens.consumeVerification(raw));
    }

    @Test
    void garbageTokenRejected() {
        assertThrows(InvalidTokenException.class, () -> tokens.consumeReset("not-a-real-token"));
    }

    @Test
    void expiredVerificationTokenRejected() {
        User u = users.save(User.create("tok-exp@example.com", "TokExp", "h"));
        String raw = "expired-raw-token-value";
        String tokenHash = sha256Hex(raw);
        VerificationToken expired = VerificationToken.of(u.getId(), tokenHash,
            Instant.now().minusSeconds(1));
        verificationTokens.save(expired);

        assertThrows(InvalidTokenException.class, () -> tokens.consumeVerification(raw));
    }

    @Test
    void resetRoundTrip_singleUse() {
        User u = users.save(User.create("tok-reset@example.com", "TokReset", "h"));
        String raw = tokens.issueReset(u);

        User consumed = tokens.consumeReset(raw);
        assertEquals(u.getId(), consumed.getId());

        // Second consume must fail (single-use).
        assertThrows(InvalidTokenException.class, () -> tokens.consumeReset(raw));
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
