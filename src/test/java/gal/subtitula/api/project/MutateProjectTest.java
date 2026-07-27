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
                     "baseBox":{"xPct":50,"yPct":92,"widthPct":85,"align":"center"},
                     "segments":[{"startSec":3.5,"style":{"color":"#ff0000"},
                                  "box":{"xPct":20,"yPct":30,"widthPct":40,"align":"left"}}],
                     "words":[{"text":"Ola","start":0.0,"end":0.3,"type":"word"}]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed"))
            .andExpect(jsonPath("$.speedFactor").value(2.0))
            .andExpect(jsonPath("$.style.preset").value("boxed"))
            .andExpect(jsonPath("$.baseBox.widthPct").value(85))
            .andExpect(jsonPath("$.segments.length()").value(1))
            .andExpect(jsonPath("$.segments[0].startSec").value(3.5))
            .andExpect(jsonPath("$.segments[0].box.align").value("left"))
            .andExpect(jsonPath("$.words.length()").value(1))
            .andExpect(jsonPath("$.words[0].text").value("Ola"));
    }

    @Test
    void approvalIsRecordedOnceAndFreezesTheContent() throws Exception {
        Cookie a = registerAndSession("approver@example.com");
        String id = createProject(a);

        mockMvc.perform(get("/projects/" + id).cookie(a))
            .andExpect(jsonPath("$.approvedAt").doesNotExist());

        mockMvc.perform(patch("/projects/" + id).with(csrf()).cookie(a)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvedAt").isNotEmpty());

        // Read the stored instant back: Postgres keeps microseconds, so only a
        // round-tripped value is comparable to a later round-tripped one.
        MvcResult stored = mockMvc.perform(get("/projects/" + id).cookie(a))
            .andExpect(status().isOk()).andReturn();
        String firstInstant = com.jayway.jsonpath.JsonPath.read(
            stored.getResponse().getContentAsString(), "$.approvedAt");

        // Content is now fixed — a new version is required to change it.
        mockMvc.perform(patch("/projects/" + id).with(csrf()).cookie(a)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"words\":[{\"text\":\"Novo\",\"start\":0.0,\"end\":0.3,\"type\":\"word\"}]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("project_approved"));

        // Approving again must not move the recorded instant; renaming still works.
        mockMvc.perform(patch("/projects/" + id).with(csrf()).cookie(a)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true,\"name\":\"Pleno de xuño\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Pleno de xuño"));

        mockMvc.perform(get("/projects/" + id).cookie(a))
            .andExpect(jsonPath("$.approvedAt").value(firstInstant))
            .andExpect(jsonPath("$.name").value("Pleno de xuño"));
    }

    @Test
    void listExposesApprovalSoTheDashboardCanShowStatus() throws Exception {
        Cookie a = registerAndSession("dashboard@example.com");
        String id = createProject(a);
        mockMvc.perform(patch("/projects/" + id).with(csrf()).cookie(a)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/projects").cookie(a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].approvedAt").isNotEmpty());
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
