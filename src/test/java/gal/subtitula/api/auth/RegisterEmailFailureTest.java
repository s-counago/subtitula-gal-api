package gal.subtitula.api.auth;

import gal.subtitula.api.email.EmailSender;
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
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that registration succeeds (201 + SESSION cookie) even when
 * the EmailSender throws — i.e. a transient SES failure must not roll back
 * an already-committed registration or hide the session from the client.
 *
 * Uses its own Spring context (throwing EmailSender) and its own Postgres
 * container rather than extending AbstractIntegrationTest, which has a hard
 * @Autowired RecordingEmailSender field that conflicts with a replaced bean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(RegisterEmailFailureTest.ThrowingEmailConfig.class)
class RegisterEmailFailureTest {

    // Stand-alone postgres container — JVM-level singleton for this context.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @TestConfiguration
    static class ThrowingEmailConfig {
        @Bean
        @Primary
        EmailSender throwingEmailSender() {
            return (to, subject, body) -> {
                throw new RuntimeException("ses down");
            };
        }
    }

    @Test
    void registerSucceedsDespiteEmailSendFailure() throws Exception {
        mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"mailfail@example.com","password":"hunter2hunter","displayName":"MailFail"}"""))
            .andExpect(status().isCreated())
            .andExpect(cookie().exists("SESSION"));
    }
}
