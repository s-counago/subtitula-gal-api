package gal.subtitula.api.auth;

import gal.subtitula.api.auth.dto.RegisterRequest;
import gal.subtitula.api.auth.dto.UserResponse;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository users;
    private final RegistrationService registrationService;
    private final SessionAuthService sessionAuthService;

    public AuthController(UserRepository users,
                          RegistrationService registrationService,
                          SessionAuthService sessionAuthService) {
        this.users = users;
        this.registrationService = registrationService;
        this.sessionAuthService = sessionAuthService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        User u = users.findById(principal.userId()).orElseThrow();
        return UserResponse.from(u);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req,
                                 HttpServletRequest request) {
        User user = registrationService.register(req);
        sessionAuthService.login(request, user);
        return UserResponse.from(user);
    }
}
