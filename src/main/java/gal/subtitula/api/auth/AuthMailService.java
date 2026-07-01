package gal.subtitula.api.auth;

import gal.subtitula.api.email.EmailSender;
import gal.subtitula.api.token.OneTimeTokenService;
import gal.subtitula.api.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthMailService {

    private final OneTimeTokenService tokens;
    private final EmailSender email;
    private final String frontendUrl;

    public AuthMailService(OneTimeTokenService tokens, EmailSender email,
                           @Value("${app.frontend.base-url}") String frontendUrl) {
        this.tokens = tokens; this.email = email; this.frontendUrl = frontendUrl;
    }

    public void sendVerification(User user) {
        String raw = tokens.issueVerification(user);
        String link = frontendUrl + "/verify-email?token=" + raw;
        email.send(user.getEmail(), "Verifica o teu email",
            "<p>Benvido/a a subtitula.gal. Verifica o teu email:</p>"
            + "<p><a href=\"" + link + "\">Verificar email</a></p>");
    }

    public void sendReset(User user) {
        String raw = tokens.issueReset(user);
        String link = frontendUrl + "/reset-password?token=" + raw;
        email.send(user.getEmail(), "Restablece o teu contrasinal",
            "<p>Recibimos unha solicitude para restablecer o teu contrasinal:</p>"
            + "<p><a href=\"" + link + "\">Restablecer contrasinal</a></p>");
    }
}
