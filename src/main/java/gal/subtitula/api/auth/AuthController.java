package gal.subtitula.api.auth;

import gal.subtitula.api.auth.dto.ForgotPasswordRequest;
import gal.subtitula.api.auth.dto.LoginRequest;
import gal.subtitula.api.auth.dto.RegisterRequest;
import gal.subtitula.api.auth.dto.ResetPasswordRequest;
import gal.subtitula.api.auth.dto.UserResponse;
import gal.subtitula.api.auth.dto.VerifyEmailRequest;
import gal.subtitula.api.token.OneTimeTokenService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Pre-computed valid BCrypt hash used to equalize timing on the no-real-hash path
    // (missing user or Google-only account). BCryptPasswordEncoder.matches() logs a
    // warning and short-circuits when the encoded value is not syntactically valid BCrypt,
    // which would both break the timing guarantee and pollute test output.
    private static final String DUMMY_HASH =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("timing-blind-dummy");

    private final UserRepository users;
    private final RegistrationService registrationService;
    private final SessionAuthService sessionAuthService;
    private final PasswordEncoder encoder;
    private final AuthMailService authMailService;
    private final OneTimeTokenService oneTimeTokenService;
    private final SessionRegistryService sessionRegistryService;

    public AuthController(UserRepository users,
                          RegistrationService registrationService,
                          SessionAuthService sessionAuthService,
                          PasswordEncoder encoder,
                          AuthMailService authMailService,
                          OneTimeTokenService oneTimeTokenService,
                          SessionRegistryService sessionRegistryService) {
        this.users = users;
        this.registrationService = registrationService;
        this.sessionAuthService = sessionAuthService;
        this.encoder = encoder;
        this.authMailService = authMailService;
        this.oneTimeTokenService = oneTimeTokenService;
        this.sessionRegistryService = sessionRegistryService;
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
        try {
            authMailService.sendVerification(user);
        } catch (Exception e) {
            log.warn("Verification email send failed for user {}: {}", user.getId(), e.toString());
        }
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        // Always run exactly one BCrypt verification to prevent timing-based email
        // enumeration (mirrors Spring Security's DaoAuthenticationProvider behaviour).
        java.util.Optional<User> found = users.findByEmail(req.email().toLowerCase());
        User candidate = found.orElse(null);
        String hash = (candidate != null && candidate.getPasswordHash() != null)
                ? candidate.getPasswordHash()
                : DUMMY_HASH;
        boolean matched = encoder.matches(req.password(), hash);
        if (candidate == null || candidate.getPasswordHash() == null || !matched) {
            throw new InvalidCredentialsException();
        }
        sessionAuthService.login(request, response, candidate);
        return UserResponse.from(candidate);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        User user = oneTimeTokenService.consumeVerification(req.token());
        user.setEmailVerified(true);
        users.save(user);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@AuthenticationPrincipal AuthPrincipal principal) {
        User user = users.findById(principal.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isEmailVerified()) {
            // Best-effort, consistent with register/forgot-password: a transient mailer
            // failure must not surface as a 500 to the client.
            try {
                authMailService.sendVerification(user);
            } catch (Exception e) {
                log.warn("Resend verification email failed for user {}: {}", user.getId(), e.toString());
            }
        }
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        users.findByEmail(req.email().toLowerCase()).ifPresent(user -> {
            try {
                authMailService.sendReset(user);
            } catch (Exception e) {
                log.warn("Password reset email send failed for user {}: {}", user.getId(), e.toString());
            }
        });
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        User user = oneTimeTokenService.consumeReset(req.token());
        user.setPasswordHash(encoder.encode(req.password()));
        users.save(user);
        sessionRegistryService.invalidateAllForPrincipal(user.getId().toString());
    }
}
