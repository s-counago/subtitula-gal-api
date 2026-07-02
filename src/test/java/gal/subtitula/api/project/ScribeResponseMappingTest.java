package gal.subtitula.api.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScribeResponseMappingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsRealScribeResponseToWords() throws Exception {
        var json = mapper.readTree(
            Files.readString(Path.of("src/test/resources/fixtures/scribe-sample.json")));

        TranscriptionResult result = ElevenLabsScribeClient.mapResponse(json);

        assertThat(result.languageCode()).isNotBlank();
        assertThat(result.words()).isNotEmpty();
        Word first = result.words().get(0);
        assertThat(first.text()).isNotNull();
        assertThat(first.end()).isGreaterThanOrEqualTo(first.start());
        assertThat(result.words()).anyMatch(w -> "word".equals(w.type()));
        // Timings are non-decreasing across the transcript.
        for (int i = 1; i < result.words().size(); i++) {
            assertThat(result.words().get(i).start())
                .isGreaterThanOrEqualTo(result.words().get(i - 1).start());
        }
    }
}
