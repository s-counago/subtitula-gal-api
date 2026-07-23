package gal.subtitula.api.oauth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestPropertySource(properties =
    "spring.security.oauth2.client.registration.google.redirect-uri="
        + "https://web.example/backend/login/oauth2/code/google")
class GoogleOAuthConfigurationTest extends AbstractIntegrationTest {

    @Autowired
    ClientRegistrationRepository registrations;

    @Test
    void usesTheEnvironmentSelectedPublicCallback() {
        assertEquals(
            "https://web.example/backend/login/oauth2/code/google",
            registrations.findByRegistrationId("google").getRedirectUri());
    }
}
