package gal.subtitula.api.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/** Minimal principal stored in the session. */
public record AuthPrincipal(UUID userId, String email)
        implements java.security.Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Returns the user's UUID string — used by Spring Session as the principal-name index key. */
    @Override
    public String getName() { return userId.toString(); }
}
