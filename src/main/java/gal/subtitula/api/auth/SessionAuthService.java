package gal.subtitula.api.auth;

import gal.subtitula.api.user.User;
import jakarta.servlet.http.HttpServletRequest;
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
    public void login(HttpServletRequest request, User user) {
        AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getEmail());
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        // Persist into the (Spring Session-backed) HttpSession so a SESSION cookie is issued.
        request.getSession(true);
        contextRepository.saveContext(context, request, currentResponse());
    }

    // saveContext needs the response; AuthController passes it through a ThreadLocal-free path.
    private static jakarta.servlet.http.HttpServletResponse currentResponse() {
        return ((org.springframework.web.context.request.ServletRequestAttributes)
            org.springframework.web.context.request.RequestContextHolder
                .currentRequestAttributes()).getResponse();
    }
}
