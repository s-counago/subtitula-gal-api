package gal.subtitula.api.project;

import java.util.Locale;

/** Product scope: Galician by default, Spanish only when explicitly selected. */
public final class TranscriptionLanguage {
    private TranscriptionLanguage() {}

    public static String select(String value) {
        if (value == null || value.isBlank()) return "glg";
        String language = value.trim().toLowerCase(Locale.ROOT);
        if (!language.equals("glg") && !language.equals("spa")) {
            throw new UnsupportedTranscriptionLanguageException();
        }
        return language;
    }
}
