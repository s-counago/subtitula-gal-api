package gal.subtitula.api.token;

import gal.subtitula.api.support.AbstractIntegrationTest;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class OneTimeTokenServiceTest extends AbstractIntegrationTest {

    @Autowired OneTimeTokenService tokens;
    @Autowired UserRepository users;

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
}
