package gal.subtitula.api.token;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() { super("invalid or expired token"); }
}
