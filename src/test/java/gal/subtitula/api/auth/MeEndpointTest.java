package gal.subtitula.api.auth;

import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeEndpointTest extends AbstractIntegrationTest {
    @Test
    void meReturns401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized());
    }
}
