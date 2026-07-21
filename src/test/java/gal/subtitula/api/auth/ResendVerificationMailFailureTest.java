package gal.subtitula.api.auth;

import gal.subtitula.api.email.EmailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resending the verification email must be best-effort like registration: a transient
 * mailer failure must not surface as a 500. Uses its own context with a throwing
 * EmailSender (mirrors {@link RegisterEmailFailureTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(ResendVerificationMailFailureTest.ThrowingEmailConfig.class)
class ResendVerificationMailFailureTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", gal.subtitula.api.support.AbstractIntegrationTest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", gal.subtitula.api.support.AbstractIntegrationTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", gal.subtitula.api.support.AbstractIntegrationTest.POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @TestConfiguration
    static class ThrowingEmailConfig {
        @Bean
        @Primary
        EmailSender throwingEmailSender() {
            return (to, subject, body) -> {
                throw new RuntimeException("email provider down");
            };
        }
    }

    @Test
    void resendReturns204DespiteEmailSendFailure() throws Exception {
        // Register (verification email fails but is swallowed) → authenticated session.
        MvcResult registered = mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"resendfail@example.com","password":"hunter2hunter","displayName":"Resend"}"""))
            .andExpect(status().isCreated())
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        Cookie session = registered.getResponse().getCookie("SESSION");

        // Explicit resend must not 500 when the mailer throws — best-effort, returns 204.
        mockMvc.perform(post("/auth/resend-verification").with(csrf()).cookie(session))
            .andExpect(status().isNoContent());
    }
}
