package gal.subtitula.api.ratelimit;

import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behind a load balancer every request shares the proxy's socket address, so the limiter
 * must key off the forwarded client IP — otherwise all users collapse into one bucket
 * (limiter ineffective) or one abuser locks everyone out. Requires
 * {@code server.forward-headers-strategy=framework} so X-Forwarded-For reaches getRemoteAddr().
 */
@TestPropertySource(properties = "app.ratelimit.capacity=2")
class RateLimitProxyTest extends AbstractIntegrationTest {

    private void login(String forwardedFor, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mockMvc.perform(post("/auth/login").with(csrf())
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"x@example.com","password":"whatever12"}"""))
            .andExpect(expected);
    }

    @Test
    void limiterKeysOffForwardedClientIp() throws Exception {
        // Client A exhausts its own bucket (capacity 2).
        login("1.1.1.1", status().isUnauthorized());
        login("1.1.1.1", status().isUnauthorized());
        login("1.1.1.1", status().isTooManyRequests());

        // Client B, distinguished only by X-Forwarded-For, still has its own bucket.
        login("2.2.2.2", status().isUnauthorized());
    }
}
