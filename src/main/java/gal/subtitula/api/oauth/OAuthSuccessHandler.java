package gal.subtitula.api.oauth;

import gal.subtitula.api.auth.SessionAuthService;
import gal.subtitula.api.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthUserService oauthUsers;
    private final SessionAuthService sessionAuth;
    private final String frontendUrl;

    public OAuthSuccessHandler(OAuthUserService oauthUsers, SessionAuthService sessionAuth,
                               @Value("${app.frontend.base-url}") String frontendUrl) {
        this.oauthUsers = oauthUsers;
        this.sessionAuth = sessionAuth;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String sub = principal.getName(); // Google 'sub'
        String email = principal.getAttribute("email");
        Boolean verified = principal.getAttribute("email_verified");
        String name = principal.getAttribute("name");

        try {
            User user = oauthUsers.findOrCreate(sub, email, Boolean.TRUE.equals(verified),
                name != null ? name : email);
            sessionAuth.login(request, response, user);
            response.sendRedirect(frontendUrl + "/upload");
        } catch (EmailConflictException e) {
            response.sendRedirect(frontendUrl + "/login?error=email_conflict");
        }
    }
}
