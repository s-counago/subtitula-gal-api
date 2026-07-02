package gal.subtitula.api.project;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MutateProjectTest extends AbstractIntegrationTest {

    private String createProject(Cookie session) throws Exception {
        var file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1, 2, 3});
        MvcResult r = mockMvc.perform(multipart("/projects").file(file)
                .param("name", "Orig").with(csrf()).cookie(session))
            .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void patchUpdatesEditableFields() throws Exception {
        Cookie a = registerAndSession("patcher@example.com");
        String id = createProject(a);

        mockMvc.perform(patch("/projects/" + id).with(csrf()).cookie(a)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Renamed","speedFactor":2.0,
                     "style":{"preset":"boxed"},
                     "words":[{"text":"Ola","start":0.0,"end":0.3,"type":"word"}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed"))
            .andExpect(jsonPath("$.speedFactor").value(2.0))
            .andExpect(jsonPath("$.style.preset").value("boxed"))
            .andExpect(jsonPath("$.words.length()").value(1))
            .andExpect(jsonPath("$.words[0].text").value("Ola"));
    }

    @Test
    void patchByNonOwnerIs404() throws Exception {
        Cookie a = registerAndSession("owner-patch@example.com");
        Cookie b = registerAndSession("intruder-patch@example.com");
        String id = createProject(a);
        mockMvc.perform(patch("/projects/" + id).with(csrf()).cookie(b)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"hax\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesProjectForOwnerOnly() throws Exception {
        Cookie a = registerAndSession("deleter@example.com");
        Cookie b = registerAndSession("intruder-del@example.com");
        String id = createProject(a);

        mockMvc.perform(delete("/projects/" + id).with(csrf()).cookie(b))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/projects/" + id).with(csrf()).cookie(a))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/projects/" + id).cookie(a))
            .andExpect(status().isNotFound());
    }
}
