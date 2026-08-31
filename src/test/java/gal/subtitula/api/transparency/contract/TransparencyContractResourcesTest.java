package gal.subtitula.api.transparency.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class TransparencyContractResourcesTest {

    private static final Path CONTRACTS = Path.of("src/main/resources/contracts");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everySchemaUsesDraft202012AndFailsClosedOnUnknownTopLevelFields() throws IOException {
        for (String filename : new String[]{
                "normalized-transcript-v1.schema.json",
                "session-guide-v1.schema.json",
                "search-evaluation-v1.schema.json"}) {
            JsonNode schema = mapper.readTree(Files.readString(CONTRACTS.resolve(filename)));
            assertThat(schema.path("$schema").asText())
                .as(filename)
                .isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schema.path("$id").asText()).as(filename).isNotBlank();
            assertThat(schema.path("additionalProperties").asBoolean(true))
                .as(filename)
                .isFalse();
            assertThat(schema.path("required").isArray()).as(filename).isTrue();
        }
    }

    @Test
    void evaluationSeedHasStableUniqueIdsAndHonestNoAnswerRows() throws IOException {
        var ids = new HashSet<String>();
        var lines = Files.readAllLines(
            Path.of("src/test/resources/fixtures/search-evaluation-seed.jsonl"));

        assertThat(lines).hasSizeGreaterThanOrEqualTo(6);
        for (String line : lines) {
            JsonNode row = mapper.readTree(line);
            assertThat(row.path("schemaVersion").asText()).isEqualTo("1.0.0");
            assertThat(row.path("query").asText()).isNotBlank();
            assertThat(ids.add(row.path("id").asText())).isTrue();
            if (row.path("expectedNoAnswer").asBoolean()) {
                assertThat(row.path("intent").asText()).isEqualTo("no_answer");
                assertThat(row.path("relevantEvidenceRefs").isEmpty()).isTrue();
            } else {
                assertThat(row.path("relevantEvidenceRefs").isEmpty()).isFalse();
            }
        }
    }
}
