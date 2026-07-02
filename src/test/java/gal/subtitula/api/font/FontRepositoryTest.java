package gal.subtitula.api.font;

import gal.subtitula.api.support.AbstractIntegrationTest;
import gal.subtitula.api.user.User;
import gal.subtitula.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FontRepositoryTest extends AbstractIntegrationTest {

    @Autowired FontRepository fonts;
    @Autowired UserRepository users;

    private UUID newUser() {
        return users.save(User.create("f" + UUID.randomUUID() + "@example.com", "F", "x")).getId();
    }

    @Test
    void roundTripsBytesAndScopesByUser() {
        UUID a = newUser();
        UUID b = newUser();
        byte[] payload = {10, 20, 30, 40};
        Font saved = fonts.save(Font.create(a, "Comic Neue", "font/ttf", payload));

        Font reloaded = fonts.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFamily()).isEqualTo("Comic Neue");
        assertThat(reloaded.getContentType()).isEqualTo("font/ttf");
        assertThat(reloaded.getSizeBytes()).isEqualTo(4);
        assertThat(reloaded.getBytes()).containsExactly(10, 20, 30, 40);

        assertThat(fonts.findByIdAndUserId(saved.getId(), b)).isEmpty();
        assertThat(fonts.findByUserIdOrderByCreatedAtDesc(a)).hasSize(1);
    }
}
