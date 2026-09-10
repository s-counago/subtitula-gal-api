package gal.subtitula.api.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> AUTH_LIMITED = Set.of(
        "/auth/login", "/auth/forgot-password", "/auth/reset-password", "/auth/resend-verification");

    private final Cache<String, Bucket> authBuckets;
    private final Cache<String, Bucket> publicSearchBuckets;
    private final long authCapacity;
    private final long publicSearchCapacity;

    public RateLimitFilter(
            @Value("${app.ratelimit.capacity:10}") long authCapacity,
            @Value("${app.ratelimit.public-search-capacity:60}")
            long publicSearchCapacity) {
        this.authCapacity = authCapacity;
        this.publicSearchCapacity = publicSearchCapacity;
        this.authBuckets = bucketCache();
        this.publicSearchBuckets = bucketCache();
    }

    private static Cache<String, Bucket> bucketCache() {
        return Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (isAuthMutation(req)
                && !consume(authBuckets, req.getRequestURI() + "|" + req.getRemoteAddr(),
                    authCapacity)) {
            reject(res);
            return;
        }
        if (isPublicSearch(req)
                && !consume(publicSearchBuckets, req.getRemoteAddr(), publicSearchCapacity)) {
            reject(res);
            return;
        }
        chain.doFilter(req, res);
    }

    private static boolean consume(
            Cache<String, Bucket> buckets,
            String key,
            long capacity) {
        Bucket bucket = buckets.get(key, ignored -> Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(1)).build())
            .build());
        return bucket != null && bucket.tryConsume(1);
    }

    private static boolean isAuthMutation(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
            && AUTH_LIMITED.contains(request.getRequestURI());
    }

    private static boolean isPublicSearch(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (HttpMethod.GET.matches(request.getMethod())) {
            return path.equals("/public/search")
                || path.matches("^/public/sessions/[^/]+/search$");
        }
        return HttpMethod.POST.matches(request.getMethod())
            && path.equals("/public/search/clicks");
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"error\":\"rate_limited\",\"message\":\"Too many requests. Try again later.\"}");
    }
}
