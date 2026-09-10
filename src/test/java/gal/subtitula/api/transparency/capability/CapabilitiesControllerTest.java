package gal.subtitula.api.transparency.capability;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CapabilitiesControllerTest extends AbstractIntegrationTest {

    @Test
    void anonymousClientReceivesSafeRolloutDefaults() throws Exception {
        mockMvc.perform(get("/capabilities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contractVersion").value("1.0.0"))
            .andExpect(jsonPath("$.durableInstitutionalUpload").value(false))
            .andExpect(jsonPath("$.normalizedTranscript").value(true))
            .andExpect(jsonPath("$.exceptionReview").value(false))
            .andExpect(jsonPath("$.automaticAgenda").value(false))
            .andExpect(jsonPath("$.structuredGuide").value(false))
            .andExpect(jsonPath("$.publicPublication").value(false))
            .andExpect(jsonPath("$.lexicalSearch").value(false))
            .andExpect(jsonPath("$.hybridSearch").value(false));
    }

    @Test
    void disabledCapabilitiesAreClosedOnTheServerNotOnlyHiddenInTheUi()
            throws Exception {
        Cookie owner = registerAndSession("closed-capability@example.com");
        mockMvc.perform(post("/projects")
                .cookie(owner)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Pleno", "language":"glg", "sessionType":"plenary"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("capability_disabled"))
            .andExpect(jsonPath("$.capability")
                .value("durable_institutional_upload"));

        mockMvc.perform(get("/public/search").param("q", "orzamento"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("capability_disabled"))
            .andExpect(jsonPath("$.capability").value("lexical_search"));
    }
}
