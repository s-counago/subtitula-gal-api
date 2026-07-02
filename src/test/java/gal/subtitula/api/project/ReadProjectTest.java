package gal.subtitula.api.project;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReadProjectTest extends AbstractIntegrationTest {

    private String createProject(Cookie session, String name) throws Exception {
        var file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1, 2, 3});
        MvcResult r = mockMvc.perform(multipart("/projects").file(file)
                .param("name", name).with(csrf()).cookie(session))
            .andExpect(status().isCreated()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void listReturnsOnlyOwnProjects() throws Exception {
        Cookie a = registerAndSession("a-read@example.com");
        Cookie b = registerAndSession("b-read@example.com");
        createProject(a, "A one");
        createProject(a, "A two");
        createProject(b, "B one");

        mockMvc.perform(get("/projects").cookie(a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getByIdReturnsFullProjectForOwner() throws Exception {
        Cookie a = registerAndSession("owner-get@example.com");
        String id = createProject(a, "Mine");
        mockMvc.perform(get("/projects/" + id).cookie(a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Mine"))
            .andExpect(jsonPath("$.words.length()").value(3));
    }

    @Test
    void getByIdIs404ForNonOwner() throws Exception {
        Cookie a = registerAndSession("owner-404@example.com");
        Cookie b = registerAndSession("intruder-404@example.com");
        String id = createProject(a, "Secret");
        mockMvc.perform(get("/projects/" + id).cookie(b))
            .andExpect(status().isNotFound());
    }
}
