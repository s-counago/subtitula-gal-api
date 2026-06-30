package gal.subtitula.api.auth;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("invalid credentials"); }
}
