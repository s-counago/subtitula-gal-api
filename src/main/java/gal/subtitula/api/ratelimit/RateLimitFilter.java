package gal.subtitula.api.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED = Set.of(
        "/auth/login", "/auth/forgot-password", "/auth/reset-password", "/auth/resend-verification");

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long capacity;

    public RateLimitFilter(@Value("${app.ratelimit.capacity:10}") long capacity) {
        this.capacity = capacity;
    }

    private Bucket newBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(1)).build())
            .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        boolean limited = HttpMethod.POST.matches(req.getMethod()) && LIMITED.contains(req.getRequestURI());
        if (limited) {
            String key = req.getRequestURI() + "|" + req.getRemoteAddr();
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
            if (!bucket.tryConsume(1)) {
                res.setStatus(429);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many requests. Try again later.\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
