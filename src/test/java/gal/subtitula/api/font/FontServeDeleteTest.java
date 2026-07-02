package gal.subtitula.api.font;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FontServeDeleteTest extends AbstractIntegrationTest {

    private String uploadFont(Cookie session) throws Exception {
        var file = new MockMultipartFile("file", "F.ttf", "font/ttf", new byte[]{9, 8, 7});
        MvcResult r = mockMvc.perform(multipart("/fonts").file(file)
                .param("family", "F").with(csrf()).cookie(session))
            .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void servesBytesToOwner() throws Exception {
        Cookie a = registerAndSession("serve@example.com");
        String id = uploadFont(a);
        MvcResult r = mockMvc.perform(get("/fonts/" + id).cookie(a))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "font/ttf"))
            .andReturn();
        assertThat(r.getResponse().getContentAsByteArray()).containsExactly(9, 8, 7);
    }

    @Test
    void serveIs404ForNonOwner() throws Exception {
        Cookie a = registerAndSession("serve-owner@example.com");
        Cookie b = registerAndSession("serve-intruder@example.com");
        String id = uploadFont(a);
        mockMvc.perform(get("/fonts/" + id).cookie(b)).andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesForOwnerOnly() throws Exception {
        Cookie a = registerAndSession("del-owner@example.com");
        Cookie b = registerAndSession("del-intruder@example.com");
        String id = uploadFont(a);
        mockMvc.perform(delete("/fonts/" + id).with(csrf()).cookie(b)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/fonts/" + id).with(csrf()).cookie(a)).andExpect(status().isNoContent());
        mockMvc.perform(get("/fonts/" + id).cookie(a)).andExpect(status().isNotFound());
    }
}
