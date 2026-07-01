package gal.subtitula.api.oauth;

/** Thrown when a Google account with an unverified email conflicts with an existing password account. */
public class EmailConflictException extends RuntimeException {
    public EmailConflictException(String email) {
        super("Email already registered with a password account: " + email);
    }
}
