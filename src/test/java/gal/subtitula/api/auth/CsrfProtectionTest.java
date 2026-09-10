package gal.subtitula.api.auth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF must be usable by a cookie/header SPA: Spring Security 6 defers CSRF token
 * loading, so unless a safe request materialises the token the XSRF-TOKEN cookie is
 * never sent and the SPA's first POST is rejected. Forks a high-capacity context so
 * the login probe isn't throttled by shared rate-limit buckets.
 */
@TestPropertySource(properties = "app.ratelimit.capacity=1000")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfProtectionTest extends AbstractIntegrationTest {
    @Test
    void errorDispatchPreservesStatusWithoutExposingDirectErrorRoute() throws Exception {
        mockMvc.perform(get("/error"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/error")
                .with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"nocsrf@example.com","password":"hunter2hunter","displayName":"NoCsrf"}"""))
            .andExpect(status().isForbidden());
    }

    @Test
    void safeRequestSetsXsrfCookie() throws Exception {
        MvcResult res = mockMvc.perform(get("/ping"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie xsrf = res.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "a safe GET must deliver the XSRF-TOKEN cookie to the SPA");
        assertFalse(xsrf.getValue().isBlank(), "XSRF-TOKEN cookie must carry a token value");
    }

    @Test
    void postSucceedsWithTokenFromCookieInHeader() throws Exception {
        // SPA bootstrap: read the token from the cookie a safe request delivered...
        Cookie xsrf = mockMvc.perform(get("/ping"))
            .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "safe GET must deliver XSRF-TOKEN cookie");

        // ...then echo it back in the X-XSRF-TOKEN header on the next POST.
        mockMvc.perform(post("/auth/login")
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"whoever@example.com","password":"whatever12"}"""))
            // CSRF passes → request reaches the controller → generic 401 (not a 403 CSRF reject).
            .andExpect(status().isUnauthorized());
    }
}
