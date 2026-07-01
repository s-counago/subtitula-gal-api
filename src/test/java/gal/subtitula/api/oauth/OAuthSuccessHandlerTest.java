package gal.subtitula.api.oauth;

import gal.subtitula.api.auth.SessionAuthService;
import gal.subtitula.api.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthSuccessHandlerTest {

    /** Records whether the (unreachable-without-email) find-or-create path was invoked. */
    static final class RecordingOAuthUserService extends OAuthUserService {
        boolean called = false;
        RecordingOAuthUserService() { super(null, null); }
        @Override
        public User findOrCreate(String googleSub, String email, boolean emailVerified, String displayName) {
            called = true;
            return null;
        }
    }

    @Test
    void redirectsToErrorWhenGoogleReturnsNoEmail() throws Exception {
        RecordingOAuthUserService oauthUsers = new RecordingOAuthUserService();
        OAuthSuccessHandler handler =
            new OAuthSuccessHandler(oauthUsers, new SessionAuthService(), "http://front");

        // Google principal with a subject but NO email attribute.
        DefaultOAuth2User principal = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub", "google-sub-123"),
            "sub");
        var auth = new TestingAuthenticationToken(principal, null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        String redirect = response.getRedirectedUrl();
        assertNotNull(redirect, "must redirect instead of throwing on a missing email");
        assertTrue(redirect.startsWith("http://front/login?error="),
            "must redirect to the frontend login with an error, was: " + redirect);
        assertFalse(oauthUsers.called, "must not attempt user creation/linking without an email");
    }
}
