# Transcription fixtures

## `scribe-sample.json`

**Provenance: hand-authored to ElevenLabs' *documented* Scribe (`scribe_v1`) response shape — NOT yet a real API capture.**

Decision (2026-07-01): build the transcription mapping against the documented
shape now, and replace this file with a real captured response **before Phase 2**
(the frontend consumes the `Word` JSON shape verified here). The spec lists the
exact-shape + Galician check as "Phase-1 verification, non-blocking now".

### To capture the real fixture (run before Phase 2)

```bash
export ELEVENLABS_API_KEY=sk_...        # dev key, from your shell only — never commit it
curl -s -X POST https://api.elevenlabs.io/v1/speech-to-text \
  -H "xi-api-key: $ELEVENLABS_API_KEY" \
  -F "model_id=scribe_v1" \
  -F "file=@/path/to/galician-sample.mp4" \
  > src/test/resources/fixtures/scribe-sample.json
```

Then confirm the top-level fields are `language_code` / `language_probability` /
`text` / `words[]`, each word has `text`/`start`/`end`/`type`, and compare
auto-detect vs. `-F "language_code=glg"` for Galician quality. If field names
differ, reconcile `ElevenLabsScribeClient.mapResponse(...)` and this fixture's
assertions. `ScribeResponseMappingTest` must stay green against the real file.
