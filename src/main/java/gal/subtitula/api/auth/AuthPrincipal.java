package gal.subtitula.api.auth;

import java.io.Serializable;
import java.util.UUID;

/** Minimal principal stored in the session. */
public record AuthPrincipal(UUID userId, String email) implements Serializable {}
