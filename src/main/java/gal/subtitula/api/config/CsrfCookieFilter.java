package gal.subtitula.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Eagerly materialises the deferred CSRF token so {@code CookieCsrfTokenRepository}
 * writes the {@code XSRF-TOKEN} cookie on every request. Spring Security 6 loads the
 * token lazily; without this a cookie/header SPA never receives the token and its first
 * state-changing POST is rejected with 403.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Accessing the token value triggers the repository to render the cookie.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
