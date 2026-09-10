# Transcription fixtures

## `scribe-sample.json`

**Provenance: real `scribe_v2` API capture (2026-07-29)** — 20 s of the spoken
Galician Wikipedia article "Galicia" (Wikimedia Commons, `Gal-Galicia 1 of
4-article.ogg`, CC BY-SA 3.0).

The request set `language_code=glg`, `diarize=true`, and word timestamps. The real
response includes `speaker_id`, `logprob`, and `audio_duration_secs`; these are part of
the current provider contract. The committed fixture removes only the provider
`transcription_id`, because request identifiers do not belong in Git.

Capture metrics, safe failure behavior, list-rate estimate, and current API conclusions
are documented in `docs/product/scribe-v2-provider-baseline.md`.

### To re-capture the fixture

```bash
export ELEVENLABS_API_KEY=sk_...        # dev key, from your shell only — never commit it
curl -s -X POST https://api.elevenlabs.io/v1/speech-to-text \
  -H "xi-api-key: $ELEVENLABS_API_KEY" \
  -F "model_id=scribe_v2" \
  -F "language_code=glg" \
  -F "diarize=true" \
  -F "timestamps_granularity=word" \
  -F "file=@/path/to/galician-sample.mp4" \
  > /tmp/scribe-sample-raw.json
```

Sanitize the raw response by retaining only `language_code`, `language_probability`,
`text`, `words[]`, and `audio_duration_secs`. Each word retains
`text`/`start`/`end`/`type`/`speaker_id`/`logprob`. Never commit the raw response,
request ID, key, private media, or account metadata. If field names differ, reconcile
the normalized transcript contract and fixture assertions before changing persistence.
