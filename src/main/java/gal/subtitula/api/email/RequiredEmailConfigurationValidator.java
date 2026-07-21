package gal.subtitula.api.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Validates any environment that declares outbound email a required capability. */
@Component
@ConditionalOnProperty(name = "app.email.delivery-required", havingValue = "true")
public class RequiredEmailConfigurationValidator {

    public RequiredEmailConfigurationValidator(
        @Value("${app.email.provider:}") String provider,
        @Value("${spring.mail.password:}") String password,
        @Value("${app.email.from:}") String from
    ) {
        if (!"smtp".equals(provider)) {
            throw new IllegalStateException(
                "EMAIL_PROVIDER must be smtp when EMAIL_DELIVERY_REQUIRED=true"
            );
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "SMTP_PASSWORD is required when EMAIL_DELIVERY_REQUIRED=true"
            );
        }
        if (from == null || from.isBlank() || from.endsWith(".local")
            || from.endsWith("@workers.dev") || from.endsWith(".workers.dev")) {
            throw new IllegalStateException(
                "APP_EMAIL_FROM must use an onboarded sender domain when email delivery is required"
            );
        }
    }
}
