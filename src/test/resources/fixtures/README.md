# Transcription fixtures

## `scribe-sample.json`

**Provenance: real `scribe_v1` API capture (2026-07-02)** — 20 s of the spoken
Galician Wikipedia article "Galicia" (Wikimedia Commons, `Gal-Galicia 1 of
4-article.ogg`, CC BY-SA), auto-detect (no `language_code` sent).

Galician verification (2026-07-02): auto-detect returned `language_code: "glg"`
at 0.93 probability; forcing `-F language_code=glg` returned an **identical
transcript** at 1.0 — so leaving the language hint unset is fine for Galician.
Real responses carry extra fields the mapper ignores (`logprob` per word,
`audio_duration_secs`, `transcription_id`).

`scribe_v1` has since been retired. The mapping is kept against this historical
response, but re-capture this fixture with `scribe_v2` before treating it as a
current provider-contract test.

### To re-capture the fixture

```bash
export ELEVENLABS_API_KEY=sk_...        # dev key, from your shell only — never commit it
curl -s -X POST https://api.elevenlabs.io/v1/speech-to-text \
  -H "xi-api-key: $ELEVENLABS_API_KEY" \
  -F "model_id=scribe_v2" \
  -F "file=@/path/to/galician-sample.mp4" \
  > src/test/resources/fixtures/scribe-sample.json
```

Then confirm the top-level fields are `language_code` / `language_probability` /
`text` / `words[]`, each word has `text`/`start`/`end`/`type`, and compare
auto-detect vs. `-F "language_code=glg"` for Galician quality. If field names
differ, reconcile `ElevenLabsScribeClient.mapResponse(...)` and this fixture's
assertions. `ScribeResponseMappingTest` must stay green against the real file.
