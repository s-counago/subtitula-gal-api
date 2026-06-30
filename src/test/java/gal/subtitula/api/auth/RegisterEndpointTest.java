package gal.subtitula.api.auth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegisterEndpointTest extends AbstractIntegrationTest {

    @Test
    void registersAndIssuesSession() throws Exception {
        mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"register-new@example.com","password":"hunter2hunter","displayName":"New"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.email").value("register-new@example.com"))
            .andExpect(jsonPath("$.displayName").value("New"))
            .andExpect(jsonPath("$.emailVerified").value(false))
            .andExpect(cookie().exists("SESSION"));
    }

    @Test
    void rejectsDuplicateEmailWith409() throws Exception {
        String body = """
            {"email":"register-dup@example.com","password":"hunter2hunter","displayName":"Dup"}""";
        mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("email_already_registered"));
    }
}
