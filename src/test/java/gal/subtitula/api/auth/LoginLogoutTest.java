package gal.subtitula.api.auth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginLogoutTest extends AbstractIntegrationTest {

    @BeforeEach
    void seedUser() throws Exception {
        mockMvc.perform(post("/auth/register").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"li@example.com","password":"hunter2hunter","displayName":"Li"}"""));
    }

    @Test
    void loginThenMeThenLogout() throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"li@example.com","password":"hunter2hunter"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("li@example.com"))
            .andReturn();
        Cookie session = login.getResponse().getCookie("SESSION");

        mockMvc.perform(get("/auth/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("li@example.com"));

        mockMvc.perform(post("/auth/logout").with(csrf()).cookie(session))
            .andExpect(status().isNoContent());
    }

    @Test
    void wrongPasswordIsGeneric401() throws Exception {
        mockMvc.perform(post("/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"li@example.com","password":"wrongwrong"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void unknownEmailIsSameGeneric401() throws Exception {
        mockMvc.perform(post("/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"nobody@example.com","password":"whatever12"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }
}
