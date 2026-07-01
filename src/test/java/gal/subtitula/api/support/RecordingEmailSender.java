package gal.subtitula.api.support;

import gal.subtitula.api.email.EmailSender;
import java.util.ArrayList;
import java.util.List;

public class RecordingEmailSender implements EmailSender {
    public record Sent(String to, String subject, String body) {}
    public final List<Sent> sent = new ArrayList<>();

    @Override public void send(String to, String subject, String body) {
        sent.add(new Sent(to, subject, body));
    }
    public Sent last() { return sent.get(sent.size() - 1); }
}
