package gal.subtitula.api.ratelimit;

import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
    "app.capabilities.lexical-search=true",
    "app.ratelimit.public-search-capacity=2"
})
class PublicSearchRateLimitTest extends AbstractIntegrationTest {

    @Test
    void globalAndWithinSessionSearchShareABoundedClientBudget() throws Exception {
        mockMvc.perform(get("/public/search")
                .header("X-Forwarded-For", "203.0.113.10")
                .param("q", "orzamento"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/public/sessions/pleno-xullo/search")
                .header("X-Forwarded-For", "203.0.113.10")
                .param("q", "auga"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/public/search")
                .header("X-Forwarded-For", "203.0.113.10")
                .param("q", "tubaxes"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", "60"))
            .andExpect(jsonPath("$.error").value("rate_limited"));

        mockMvc.perform(get("/public/search")
                .header("X-Forwarded-For", "203.0.113.11")
                .param("q", "tubaxes"))
            .andExpect(status().isOk());
    }
}
