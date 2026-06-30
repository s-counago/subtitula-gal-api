package gal.subtitula.api.email;

public interface EmailSender {
    void send(String toEmail, String subject, String htmlBody);
}
