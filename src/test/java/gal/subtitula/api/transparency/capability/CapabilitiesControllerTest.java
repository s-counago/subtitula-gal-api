package gal.subtitula.api.transparency.capability;

import gal.subtitula.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
