package gal.subtitula.api.auth;

import gal.subtitula.api.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionAuthService {

    private final SecurityContextRepository contextRepository =
        new HttpSessionSecurityContextRepository();

    /** Logs the user in: stores an authenticated SecurityContext into a fresh session. */
    public void login(HttpServletRequest request, HttpServletResponse response, User user) {
        // Session-fixation defence: if the client presented an existing (possibly
        // attacker-fixed) session, discard it so the authenticated session gets a new id.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getEmail());
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        // Persist into a fresh (Spring Session-backed) HttpSession so a new SESSION cookie is issued.
        request.getSession(true);
        contextRepository.saveContext(context, request, response);
    }
}
