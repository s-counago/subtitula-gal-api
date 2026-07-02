package gal.subtitula.api.support;

import gal.subtitula.api.project.TranscriptionClient;
import gal.subtitula.api.project.TranscriptionResult;
import gal.subtitula.api.project.Word;

import java.util.List;

/** Test double for TranscriptionClient — returns canned words, records the call.
 *  Mirrors RecordingEmailSender; tests never hit the real ElevenLabs API. */
public class RecordingTranscriptionClient implements TranscriptionClient {
    public record Call(String filename, String contentType, String languageHint, int byteCount) {}
    public final java.util.List<Call> calls = new java.util.ArrayList<>();

    public TranscriptionResult next = new TranscriptionResult("glg", List.of(
        new Word("Boas", 0.0, 0.4, "word"),
        new Word(" ", 0.4, 0.5, "spacing"),
        new Word("mundo", 0.5, 0.9, "word")));

    @Override
    public TranscriptionResult transcribe(byte[] media, String filename, String contentType, String languageHint) {
        calls.add(new Call(filename, contentType, languageHint, media.length));
        return next;
    }
}
