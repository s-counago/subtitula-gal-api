package gal.subtitula.api.auth;

import gal.subtitula.api.auth.dto.LoginRequest;
import gal.subtitula.api.auth.dto.RegisterRequest;
import gal.subtitula.api.auth.dto.UserResponse;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository users;
    private final RegistrationService registrationService;
    private final SessionAuthService sessionAuthService;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository users,
                          RegistrationService registrationService,
                          SessionAuthService sessionAuthService,
                          PasswordEncoder encoder) {
        this.users = users;
        this.registrationService = registrationService;
        this.sessionAuthService = sessionAuthService;
        this.encoder = encoder;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        User u = users.findById(principal.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return UserResponse.from(u);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        User user = registrationService.register(req);
        sessionAuthService.login(request, response, user);
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        User user = users.findByEmail(req.email().toLowerCase())
            .filter(u -> u.getPasswordHash() != null)
            .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
            .orElseThrow(InvalidCredentialsException::new);
        sessionAuthService.login(request, response, user);
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }
}
