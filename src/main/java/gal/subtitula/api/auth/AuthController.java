package gal.subtitula.api.auth;

import gal.subtitula.api.auth.dto.UserResponse;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository users;

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        User u = users.findById(principal.userId()).orElseThrow();
        return UserResponse.from(u);
    }
}
