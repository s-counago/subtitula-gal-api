package gal.subtitula.api.oauth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class OAuthUserServiceTest extends AbstractIntegrationTest {

    @Autowired OAuthUserService oauth;
    @Autowired UserRepository users;

    @Test
    void createsNewUserForUnknownGoogleAccount() {
        User u = oauth.findOrCreate("sub-1", "g1@example.com", true, "G One");
        assertTrue(u.isEmailVerified());
        assertNull(u.getPasswordHash());
    }

    @Test
    void linksToExistingPasswordAccountWhenGoogleEmailVerified() {
        User existing = users.save(User.create("link@example.com", "Pw", "hash"));
        User result = oauth.findOrCreate("sub-2", "link@example.com", true, "Linked");
        assertEquals(existing.getId(), result.getId());
    }

    @Test
    void doesNotLinkWhenGoogleEmailUnverified() {
        users.save(User.create("safe@example.com", "Pw", "hash"));
        assertThrows(EmailConflictException.class,
            () -> oauth.findOrCreate("sub-3", "safe@example.com", false, "Attacker"));
    }

    @Test
    void secondLoginReturnsSameLinkedUser() {
        User first = oauth.findOrCreate("sub-4", "repeat@example.com", true, "Repeat");
        User second = oauth.findOrCreate("sub-4", "repeat@example.com", true, "Repeat");
        assertEquals(first.getId(), second.getId());
    }

    @Test
    void createsUnverifiedUserWhenNoAccountAndGoogleUnverified() {
        User u = oauth.findOrCreate("sub-5", "fresh-unverified@example.com", false, "Fresh");
        assertFalse(u.isEmailVerified());
        assertNull(u.getPasswordHash());
    }
}
