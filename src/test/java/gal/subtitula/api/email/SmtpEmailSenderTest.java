package gal.subtitula.api.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailSenderTest {

    @Test
    void sendsHtmlMessageThroughConfiguredSmtpClient() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        new SmtpEmailSender(mailSender, "no-reply@subtitula.local")
            .send("user@example.com", "Verifica", "<p>Ola</p>");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        sent.getValue().saveChanges();
        assertThat(sent.getValue().getFrom()[0].toString()).isEqualTo("no-reply@subtitula.local");
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Verifica");
        assertThat(sent.getValue().getContentType()).startsWith("text/html");
        assertThat(sent.getValue().getContent().toString()).contains("<p>Ola</p>");
    }
}
