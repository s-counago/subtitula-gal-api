package gal.subtitula.api.auth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PasswordResetTest extends AbstractIntegrationTest {

    @Test
    void forgotAlwaysReturns204_evenForUnknownEmail() throws Exception {
        // CORRECTION 1: use string literal instead of single-line text block (invalid Java)
        mockMvc.perform(post("/auth/forgot-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ghost@example.com\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void fullResetFlow_thenLoginWithNewPassword() throws Exception {
        // Register and capture the live SESSION cookie (registration logs the user in)
        // CORRECTION 2: capture oldSession for session-invalidation assertion below
        MvcResult reg = mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"rst@example.com","password":"oldpassword1","displayName":"Rst"}
                    """))
            .andReturn();
        Cookie oldSession = reg.getResponse().getCookie("SESSION");
        assertNotNull(oldSession, "register must issue a SESSION cookie");
        emails.sent.clear();

        // CORRECTION 1: use string literal for single-line body
        mockMvc.perform(post("/auth/forgot-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rst@example.com\"}"))
            .andExpect(status().isNoContent());

        String body = emails.last().body();
        String token = body.substring(body.indexOf("token=") + 6, body.indexOf("\">"));

        mockMvc.perform(post("/auth/reset-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"newpassword9\"}"))
            .andExpect(status().isNoContent());

        // CORRECTION 2: verify the old session is now rejected (proves invalidation worked)
        mockMvc.perform(get("/auth/me").cookie(oldSession))
            .andExpect(status().isUnauthorized());

        // New password works...
        // CORRECTION 1: use string literal for single-line login bodies
        mockMvc.perform(post("/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rst@example.com\",\"password\":\"newpassword9\"}"))
            .andExpect(status().isOk());

        // ...old one no longer does.
        mockMvc.perform(post("/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"rst@example.com\",\"password\":\"oldpassword1\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void badResetTokenIs400() throws Exception {
        mockMvc.perform(post("/auth/reset-password").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"invalid-token\",\"password\":\"newpassword9\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_token"));
    }
}
