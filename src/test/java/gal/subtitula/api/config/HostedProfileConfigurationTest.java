package gal.subtitula.api.config;

import com.zaxxer.hikari.HikariDataSource;
import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("dev")
class HostedProfileConfigurationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void hostedProperties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USER", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("app.email.provider", () -> "disabled");
        registry.add("app.email.delivery-required", () -> "false");
        registry.add("CORS_ORIGINS", () -> "https://subtitula-web-dev.example.workers.dev");
        registry.add("FRONTEND_URL", () -> "https://subtitula-web-dev.example.workers.dev");
        registry.add("ELEVENLABS_API_KEY", () -> "test-elevenlabs-key");
    }

    @Autowired
    Environment environment;

    @Autowired
    HikariDataSource dataSource;

    @Test
    void developmentActivatesTheSharedHostedContract() {
        assertThat(environment.getActiveProfiles()).containsExactly("dev", "hosted");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure", Boolean.class))
            .isTrue();
        assertThat(environment.getProperty("app.cors.allowed-origins"))
            .isEqualTo("https://subtitula-web-dev.example.workers.dev");
        assertThat(environment.getProperty("app.email.provider")).isEqualTo("disabled");
        assertThat(environment.getProperty("app.email.delivery-required", Boolean.class)).isFalse();
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(5);
        assertThat(dataSource.getMinimumIdle()).isZero();
    }
}
