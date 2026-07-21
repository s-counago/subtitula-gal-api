package gal.subtitula.api.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledEmailSenderTest {

    @Test
    void failsVisiblyInsteadOfPretendingToDeliver() {
        assertThatThrownBy(() -> new DisabledEmailSender().send(
            "person@example.com", "subject", "<p>body</p>"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("use local Mailpit");
    }
}
