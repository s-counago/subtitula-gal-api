package gal.subtitula.api.auth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import gal.subtitula.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmailVerificationTest extends AbstractIntegrationTest {

    @Autowired
    UserRepository users;

    @Test
    void registerSendsVerificationEmail_andTokenVerifies() throws Exception {
        mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"ver@example.com","password":"hunter2hunter","displayName":"Ver"}"""))
            .andExpect(status().isCreated());

        // Recorded email contains the link; extract the raw token from it.
        String body = emails.last().body();
        String token = body.substring(body.indexOf("token=") + 6, body.indexOf("\">"));

        mockMvc.perform(post("/auth/verify-email").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"))
            .andExpect(status().isNoContent());

        assertTrue(users.findByEmail("ver@example.com").orElseThrow().isEmailVerified());
    }

    @Test
    void badTokenIs400() throws Exception {
        mockMvc.perform(post("/auth/verify-email").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"garbage\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_token"));
    }
}
