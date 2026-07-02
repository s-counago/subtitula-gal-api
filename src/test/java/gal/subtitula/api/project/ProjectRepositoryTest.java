package gal.subtitula.api.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import gal.subtitula.api.support.AbstractIntegrationTest;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRepositoryTest extends AbstractIntegrationTest {

    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired ObjectMapper mapper;

    private UUID newUser() {
        User u = users.save(User.create(
            "p" + UUID.randomUUID() + "@example.com", "Owner", "x"));
        return u.getId();
    }

    @Test
    void roundTripsWordsAndStyleAsJsonb() throws Exception {
        UUID owner = newUser();
        var words = List.of(new Word("Boas", 0.0, 0.4, "word"),
                            new Word(" ", 0.4, 0.5, "spacing"),
                            new Word("mundo", 0.5, 0.9, "word"));
        var style = mapper.readTree("{\"preset\":\"glass\",\"fontSizePct\":6}");

        Project saved = projects.save(Project.create(owner, "clip.mp4", "gl", 0.9, words, style));

        Project reloaded = projects.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWords()).hasSize(3);
        assertThat(reloaded.getWords().get(2).text()).isEqualTo("mundo");
        assertThat(reloaded.getStyle().get("preset").asText()).isEqualTo("glass");
        assertThat(reloaded.getSpeedFactor()).isEqualTo(1.0);
    }

    @Test
    void scopesByUser() {
        UUID a = newUser();
        UUID b = newUser();
        Project pa = projects.save(Project.create(a, "a.mp4", "gl", 1.0, List.of(), null));
        projects.save(Project.create(b, "b.mp4", "gl", 1.0, List.of(), null));

        assertThat(projects.findByUserIdOrderByCreatedAtDesc(a)).extracting(Project::getId)
            .containsExactly(pa.getId());
        assertThat(projects.findByIdAndUserId(pa.getId(), b)).isEmpty();
        assertThat(projects.findByIdAndUserId(pa.getId(), a)).isPresent();
    }
}
