package gal.subtitula.api.ratelimit;

import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.ratelimit.capacity=3")
class RateLimitTest extends AbstractIntegrationTest {

    @Test
    void blocksAfterCapacityExceeded() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email":"x@example.com","password":"whatever12"}
                            """))
                .andExpect(status().isUnauthorized()); // wrong creds, but allowed through
        }
        mockMvc.perform(post("/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"x@example.com","password":"whatever12"}
                        """))
            .andExpect(status().isTooManyRequests());
    }
}
