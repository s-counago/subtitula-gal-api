package gal.subtitula.api.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Component
@Lazy
public class SesEmailSender implements EmailSender {

    private final SesClient ses;
    private final String from;

    public SesEmailSender(@Value("${app.email.from:no-reply@subtitula.gal}") String from) {
        this.ses = SesClient.create(); // region + creds from the default AWS chain (env vars)
        this.from = from;
    }

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        ses.sendEmail(SendEmailRequest.builder()
            .source(from)
            .destination(Destination.builder().toAddresses(toEmail).build())
            .message(Message.builder()
                .subject(Content.builder().data(subject).charset("UTF-8").build())
                .body(Body.builder().html(Content.builder().data(htmlBody).charset("UTF-8").build()).build())
                .build())
            .build());
    }
}
