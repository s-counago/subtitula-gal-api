package gal.subtitula.api.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deliberate pre-domain development mode. It never invents a fallback or
 * silently claims delivery; callers receive an exception and log the failure.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "disabled")
public class DisabledEmailSender implements EmailSender {

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        throw new IllegalStateException(
            "Hosted email is disabled until a sender domain is onboarded; use local Mailpit for email E2E"
        );
    }
}
