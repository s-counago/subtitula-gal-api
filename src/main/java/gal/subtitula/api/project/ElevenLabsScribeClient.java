package gal.subtitula.api.project;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Lazy   // never instantiated in tests (a @Primary double takes over); no key needed at startup
public class ElevenLabsScribeClient implements TranscriptionClient {

    private final RestClient http;
    private final String apiKey;
    private final String modelId;

    public ElevenLabsScribeClient(
            @Value("${app.elevenlabs.base-url:https://api.elevenlabs.io}") String baseUrl,
            @Value("${app.elevenlabs.api-key:}") String apiKey,
            @Value("${app.elevenlabs.model-id:scribe_v1}") String modelId) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofMinutes(10)); // transcription of long clips is slow
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.modelId = modelId;
    }

    @Override
    public TranscriptionResult transcribe(byte[] media, String filename, String contentType, String languageHint) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model_id", modelId);
        if (languageHint != null && !languageHint.isBlank()) {
            body.add("language_code", languageHint);
        }
        body.add("file", new ByteArrayResource(media) {
            @Override public String getFilename() { return filename; }
        });

        JsonNode json = http.post()
            .uri("/v1/speech-to-text")
            .header("xi-api-key", apiKey)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

        return mapResponse(json);
    }

    /** Pure mapping from the Scribe JSON response to our domain — unit-tested against a fixture. */
    static TranscriptionResult mapResponse(JsonNode json) {
        String lang = json.path("language_code").asText(null);
        List<Word> words = new ArrayList<>();
        JsonNode arr = json.path("words");
        if (arr.isArray()) {
            for (JsonNode w : arr) {
                words.add(new Word(
                    w.path("text").asText(""),
                    w.path("start").asDouble(0),
                    w.path("end").asDouble(0),
                    w.path("type").asText("word")));
            }
        }
        return new TranscriptionResult(lang, words);
    }
}
