package gal.subtitula.api.transparency.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class InternalNonceStore {

    private final JdbcTemplate jdbc;

    public InternalNonceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean claim(UUID nonce, Instant expiresAt) {
        jdbc.update("delete from internal_request_nonces where expires_at < ?", Timestamp.from(Instant.now()));
        return jdbc.update(
            """
            insert into internal_request_nonces(nonce, expires_at)
            values (?, ?)
            on conflict (nonce) do nothing
            """,
            nonce,
            Timestamp.from(expiresAt)) == 1;
    }
}
