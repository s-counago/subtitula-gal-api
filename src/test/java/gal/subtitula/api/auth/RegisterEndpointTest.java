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
                    {"email":"new@example.com","password":"hunter2hunter","displayName":"New"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("new@example.com"))
            .andExpect(jsonPath("$.emailVerified").value(false))
            .andExpect(cookie().exists("SESSION"));
    }

    @Test
    void rejectsDuplicateEmailWith409() throws Exception {
        String body = """
            {"email":"register-dup@example.com","password":"hunter2hunter","displayName":"Dup"}""";
        mockMvc.perform(post("/auth/register").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body));
        mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("email_already_registered"));
    }
}
