package gal.subtitula.api.auth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session-fixation defence: logging in while presenting an existing session id must
 * ROTATE the session id, so an attacker who fixed a victim's pre-auth session cannot
 * ride it after authentication. Forks its own context with a huge rate-limit capacity
 * so the login call can't be throttled by buckets shared with other tests.
 */
@TestPropertySource(properties = "app.ratelimit.capacity=1000")
class SessionFixationTest extends AbstractIntegrationTest {

    @Test
    void loginRotatesExistingSessionId() throws Exception {
        // Register → immediate login establishes session S1.
        MvcResult registered = mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"fixation@example.com","password":"hunter2hunter","displayName":"Fix"}"""))
            .andExpect(status().isCreated())
            .andReturn();
        Cookie s1 = registered.getResponse().getCookie("SESSION");
        assertTrue(s1 != null && !s1.getValue().isBlank(), "registration must issue a SESSION cookie");

        // Log in again while presenting S1 (the "fixed" session).
        MvcResult loggedIn = mockMvc.perform(post("/auth/login").with(csrf()).cookie(s1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"fixation@example.com","password":"hunter2hunter"}"""))
            .andExpect(status().isOk())
            .andReturn();

        // The response must issue a *new*, non-blank SESSION cookie whose value differs from S1.
        String rotated = Arrays.stream(loggedIn.getResponse().getCookies())
            .filter(c -> "SESSION".equals(c.getName()))
            .map(Cookie::getValue)
            .filter(v -> v != null && !v.isBlank())
            .findFirst()
            .orElse(null);

        assertNotEquals(s1.getValue(), rotated,
            "login must rotate the session id (session-fixation protection)");
        assertTrue(rotated != null, "login must issue a fresh SESSION cookie");
    }
}
