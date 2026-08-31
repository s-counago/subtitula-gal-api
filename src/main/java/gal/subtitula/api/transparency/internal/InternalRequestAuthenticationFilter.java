package gal.subtitula.api.transparency.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Authenticates the private processing Worker command surface. The signature binds
 * method, canonical path, timestamp, nonce and the exact request bytes. A nonce is
 * claimed only after the signature is valid, so unauthenticated traffic cannot fill the
 * replay table.
 */
@Component
public class InternalRequestAuthenticationFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/internal/processing/";
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;
    private static final int MAX_BODY_BYTES = 64 * 1024 * 1024;
    private static final HexFormat HEX = HexFormat.of();

    private final InternalNonceStore nonces;
    private final String secret;

    public InternalRequestAuthenticationFilter(
            InternalNonceStore nonces,
            @Value("${app.processing.internal-hmac-secret:}") String secret) {
        this.nonces = nonces;
        this.secret = secret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws IOException, ServletException {
        CachedBodyRequest authenticated;
        try {
            if (secret == null || secret.isBlank()) {
                reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "internal_auth_unavailable");
                return;
            }

            long timestamp = Long.parseLong(required(request, "X-Subtitula-Timestamp"));
            UUID nonce = UUID.fromString(required(request, "X-Subtitula-Nonce"));
            String claimedDigest = required(request, "X-Subtitula-Content-SHA256");
            String claimedSignature = required(request, "X-Subtitula-Signature");
            Instant now = Instant.now();
            if (Math.abs(now.getEpochSecond() - timestamp) > MAX_CLOCK_SKEW_SECONDS) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "internal_request_expired");
                return;
            }

            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "internal_body_too_large");
                return;
            }
            String actualDigest = HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            if (!constantTimeEquals(actualDigest, claimedDigest)) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "internal_digest_invalid");
                return;
            }

            String canonical = request.getMethod().toUpperCase()
                + "\n" + request.getRequestURI()
                + "\n" + timestamp
                + "\n" + nonce
                + "\n" + actualDigest;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String actualSignature = HEX.formatHex(
                mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
            if (!constantTimeEquals(actualSignature, claimedSignature)) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "internal_signature_invalid");
                return;
            }
            if (!nonces.claim(nonce, now.plusSeconds(MAX_CLOCK_SKEW_SECONDS))) {
                reject(response, HttpServletResponse.SC_CONFLICT, "internal_request_replayed");
                return;
            }
            authenticated = new CachedBodyRequest(request, body);
        } catch (IllegalArgumentException e) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "internal_auth_invalid");
            return;
        } catch (GeneralSecurityException | RuntimeException e) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "internal_auth_unavailable");
            return;
        }
        // Authentication failures are handled above. Application exceptions must
        // keep their real status instead of being rewritten as authentication errors.
        filterChain.doFilter(authenticated, response);
    }

    private static String required(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing header");
        }
        return value;
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
            left.getBytes(StandardCharsets.US_ASCII),
            right.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static void reject(HttpServletResponse response, int status, String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int off, int len) {
                    return input.read(bytes, off, len);
                }
            };
        }
    }
}
