package gal.subtitula.api.project;

/** One token from the transcript. Absolute, immutable timings (seconds).
 *  type is "word", "spacing", or "audio_event" (ElevenLabs Scribe categories). */
public record Word(String text, double start, double end, String type) {}
