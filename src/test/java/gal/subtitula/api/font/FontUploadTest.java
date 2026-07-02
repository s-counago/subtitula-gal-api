package gal.subtitula.api.font;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FontUploadTest extends AbstractIntegrationTest {

    @Test
    void uploadThenListReturnsOwnFonts() throws Exception {
        Cookie a = registerAndSession("fontowner@example.com");
        var file = new MockMultipartFile("file", "MyFont.ttf", "font/ttf", new byte[]{1, 2, 3, 4, 5});

        mockMvc.perform(multipart("/fonts").file(file)
                .param("family", "My Font").with(csrf()).cookie(a))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.family").value("My Font"))
            .andExpect(jsonPath("$.sizeBytes").value(5));

        mockMvc.perform(get("/fonts").cookie(a))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].family").value("My Font"));
    }

    @Test
    void listExcludesOtherUsersFonts() throws Exception {
        Cookie a = registerAndSession("fa@example.com");
        Cookie b = registerAndSession("fb@example.com");
        var file = new MockMultipartFile("file", "A.ttf", "font/ttf", new byte[]{1});
        mockMvc.perform(multipart("/fonts").file(file).param("family", "A").with(csrf()).cookie(a))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/fonts").cookie(b))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void uploadRequiresAuth() throws Exception {
        var file = new MockMultipartFile("file", "A.ttf", "font/ttf", new byte[]{1});
        mockMvc.perform(multipart("/fonts").file(file).param("family", "A").with(csrf()))
            .andExpect(status().isUnauthorized());
    }
}
