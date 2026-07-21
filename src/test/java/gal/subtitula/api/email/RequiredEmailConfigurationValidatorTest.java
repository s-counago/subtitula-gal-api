package gal.subtitula.api.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredEmailConfigurationValidatorTest {

    @Test
    void rejectsDisabledProviderWhenDeliveryIsRequired() {
        assertThatThrownBy(() -> new RequiredEmailConfigurationValidator(
            "disabled", "", ""
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EMAIL_PROVIDER");
    }

    @Test
    void rejectsMissingPassword() {
        assertThatThrownBy(() -> new RequiredEmailConfigurationValidator(
            "smtp", "", "no-reply@example.com"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SMTP_PASSWORD");
    }

    @Test
    void rejectsSharedWorkersDevSender() {
        assertThatThrownBy(() -> new RequiredEmailConfigurationValidator(
            "smtp", "token", "no-reply@account.workers.dev"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("onboarded sender domain");
    }

    @Test
    void acceptsConfiguredRequiredSmtp() {
        assertThatCode(() -> new RequiredEmailConfigurationValidator(
            "smtp", "token", "no-reply@example.com"
        )).doesNotThrowAnyException();
    }
}
