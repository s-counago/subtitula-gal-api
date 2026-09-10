package gal.subtitula.api.project;

public class UnsupportedTranscriptionLanguageException extends RuntimeException {
    public UnsupportedTranscriptionLanguageException() {
        super("Só se admite galego ou castelán.");
    }
}
