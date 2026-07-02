package gal.subtitula.api.project;

import java.util.List;

public record TranscriptionResult(String languageCode, List<Word> words) {}
