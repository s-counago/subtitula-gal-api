package gal.subtitula.api.auth;

import gal.subtitula.api.auth.dto.RegisterRequest;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public RegistrationService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public User register(RegisterRequest req) {
        if (users.existsByEmail(req.email().toLowerCase())) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = User.create(req.email(), req.displayName(), encoder.encode(req.password()));
        return users.save(user);
    }
}
