package gal.subtitula.api.config;

import gal.subtitula.api.oauth.OAuthSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    UrlBasedCorsConfigurationSource cors,
                                    OAuthSuccessHandler oauthSuccessHandler) throws Exception {
        http
            .cors(c -> c.configurationSource(cors))
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .authorizeHttpRequests(reg -> reg
                .requestMatchers(
                    "/ping",
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
            .oauth2Login(o -> o.successHandler(oauthSuccessHandler))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable());
        return http.build();
    }
}
