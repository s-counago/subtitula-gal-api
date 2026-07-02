package gal.subtitula.api.support;

import jakarta.servlet.http.Cookie;
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
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(AbstractIntegrationTest.EmailTestConfig.class)
public abstract class AbstractIntegrationTest {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-client-secret");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RecordingEmailSender emails;

    @Autowired
    protected RecordingTranscriptionClient transcriber;

    /** Register a fresh user and return the authenticated SESSION cookie.
     *  Shared across the project/font integration tests so each doesn't re-implement it. */
    protected Cookie registerAndSession(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"hunter2hunter\",\"displayName\":\"O\"}"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    @TestConfiguration
    static class EmailTestConfig {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() { return new RecordingEmailSender(); }

        @Bean
        @Primary
        RecordingTranscriptionClient recordingTranscriptionClient() { return new RecordingTranscriptionClient(); }
    }
}
