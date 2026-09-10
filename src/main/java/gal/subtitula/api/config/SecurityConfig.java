package gal.subtitula.api.config;

import gal.subtitula.api.oauth.OAuthSuccessHandler;
import gal.subtitula.api.ratelimit.RateLimitFilter;
import gal.subtitula.api.transparency.internal.InternalRequestAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Disable the servlet-container auto-registration of {@link RateLimitFilter} (it is a
     * {@code @Component}). It runs inside the Spring Security chain via {@code addFilterBefore};
     * without this it would also be registered as a plain container filter and run twice.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<InternalRequestAuthenticationFilter> internalRequestFilterRegistration(
            InternalRequestAuthenticationFilter filter) {
        FilterRegistrationBean<InternalRequestAuthenticationFilter> registration =
            new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    UrlBasedCorsConfigurationSource cors,
                                    OAuthSuccessHandler oauthSuccessHandler,
                                    RateLimitFilter rateLimitFilter,
                                    InternalRequestAuthenticationFilter internalRequestFilter,
                                    @Value("${app.frontend.base-url}") String frontendUrl) throws Exception {
        http
            .cors(c -> c.configurationSource(cors))
            // Cookie/header SPA CSRF: raw token in a JS-readable cookie (no XOR masking) so the
            // value the SPA echoes in X-XSRF-TOKEN matches what the server validates.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/internal/processing/**"))
            // Materialise the deferred token on every request so the XSRF-TOKEN cookie is delivered.
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .addFilterBefore(internalRequestFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(reg -> reg
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(
                    "/ping",
                    "/capabilities",
                    "/public/**",
                    "/internal/processing/**",
                    "/auth/register", "/auth/login",
                    "/auth/verify-email", "/auth/forgot-password", "/auth/reset-password",
                    "/oauth2/**", "/login/oauth2/**"
                ).permitAll()
                .anyRequest().authenticated())
            // Return 401 instead of redirecting to a login page on unauthenticated API calls.
            .exceptionHandling(e -> e.authenticationEntryPoint(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            // REST API: no redirect-after-login; suppress session creation on 401.
            .requestCache(rc -> rc.requestCache(new NullRequestCache()))
            // On OAuth failure (denied consent, provider/token error) send the browser back to
            // the SPA login with an error rather than the default (nonexistent) /login?error page.
            .oauth2Login(o -> o
                .successHandler(oauthSuccessHandler)
                .failureHandler(new SimpleUrlAuthenticationFailureHandler(
                    frontendUrl + "/login?error=oauth_failed")))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable());
        return http.build();
    }
}
