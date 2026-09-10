package gal.subtitula.api.project;

import gal.subtitula.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CreateProjectTest extends AbstractIntegrationTest {

    @Test
    void spanishRequiresExplicitSelectionAndOtherLanguagesNeverReachScribe() throws Exception {
        Cookie session = registerAndSession("selected-language@example.com");
        var file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1});
        var previous = transcriber.next;
        try {
            transcriber.next = new TranscriptionResult("spa", previous.words());
            mockMvc.perform(multipart("/projects").file(file).param("language", "spa")
                    .with(csrf()).cookie(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("spa"));
            assertThat(transcriber.calls).last().extracting(c -> c.languageHint()).isEqualTo("spa");
            int callsBefore = transcriber.calls.size();
            for (String language : java.util.List.of("eng", "fra", "auto")) {
                mockMvc.perform(multipart("/projects").file(file).param("language", language)
                        .with(csrf()).cookie(session))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("unsupported_transcription_language"));
            }
            assertThat(transcriber.calls).hasSize(callsBefore);
        } finally {
            transcriber.next = previous;
        }
    }

    @Test
    void uploadCreatesProjectWithTranscribedWords() throws Exception {
        Cookie session = registerAndSession("creator@example.com");
        var file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/projects").file(file)
                .param("name", "My clip")
                .param("workflowMode", "institution")
                .with(csrf()).cookie(session))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("My clip"))
            .andExpect(jsonPath("$.language").value("glg"))
            .andExpect(jsonPath("$.speedFactor").value(1.0))
            .andExpect(jsonPath("$.workflowMode").value("institution"))
            .andExpect(jsonPath("$.words.length()").value(3))
            .andExpect(jsonPath("$.words[2].text").value("mundo"));

        assertThat(transcriber.calls)
            .as("Scribe must be asked for Galician explicitly — auto-detect hears Spanish")
            .last().extracting(c -> c.languageHint()).isEqualTo("glg");
    }

    @Test
    void uploadRequiresAuth() throws Exception {
        var file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1});
        mockMvc.perform(multipart("/projects").file(file).param("name", "x").with(csrf()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadRequiresCsrf() throws Exception {
        Cookie session = registerAndSession("nocsrf@example.com");
        var file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[]{1});
        mockMvc.perform(multipart("/projects").file(file).param("name", "x").cookie(session))
            .andExpect(status().isForbidden());
    }
}
