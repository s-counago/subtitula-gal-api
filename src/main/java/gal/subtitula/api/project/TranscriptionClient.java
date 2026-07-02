package gal.subtitula.api.project;

/** Speech-to-text with word-level timings. Implemented by ElevenLabs Scribe;
 *  mocked in tests (see RecordingTranscriptionClient). */
public interface TranscriptionClient {
    TranscriptionResult transcribe(byte[] media, String filename, String contentType, String languageHint);
}
