# Scribe v2 provider baseline

**Captured:** 29 July 2026

**Environment:** local call using the restricted non-production Speech-to-Text key

**Purpose:** Phase 0 provider contract; this is not a quality benchmark

## Public fixture

The source is the first 20 seconds of the spoken Galician Wikipedia article
[“Galicia”](https://commons.wikimedia.org/wiki/File:Gal-Galicia_1_of_4-article.ogg),
published by LaVozDeTuRelato under CC BY-SA 3.0. The captured JSON is sanitized:
it contains the public transcript and provider signals but no transcription/request ID,
API key, signed URL, account identifier, or private media.

Request settings:

- `model_id=scribe_v2`;
- `language_code=glg`;
- `diarize=true`;
- `timestamps_granularity=word`;
- direct 20-second Ogg upload for this contract capture.

## Observed success

| Measure | Observation |
|---|---:|
| HTTP status | 200 |
| Input duration | 20.000 s |
| Input size | 238,129 bytes |
| End-to-end request latency | 1.648 s |
| Response size before sanitizing | 7,206 bytes |
| Reported audio duration | 20.000 s |
| Detected language | `glg` |
| Language probability | 1.0 |
| Word/spacing entries | 59 |
| Distinct diarization labels | 1 (`speaker_0`) |
| Per-word log probability | present |
| Audio-event entries | 0 |

At the public list rate visible during capture (`$0.22/hour` for Scribe), 20 seconds
corresponds to approximately **$0.00122** before plan-specific discounts, credits, tax,
or optional feature charges. This is an estimate, not an invoice amount. Production
margin calculations must use the actual workspace bill.

## Observed validation failure

A request with `model_id=scribe_v2` but neither a file nor URL returned:

- HTTP 400;
- provider status `invalid_parameters`;
- safe message: “Must provide either file or a URL parameter.”;
- latency 0.233 s;
- no transcription work was accepted.

Map this to the non-retryable internal code `PROVIDER_REJECTED`. Do not expose the raw
provider response directly to the frontend.

## Contract conclusions

- `diarize` defaults to false and must be sent explicitly.
- `speaker_id` and `logprob` are useful signals and must not be discarded.
- `source_url` is the current URL input. `cloud_storage_url` is deprecated.
- A direct file may be below 5 GB; `source_url` is the planned R2 path and must still be
  constrained by our upload/product policy.
- Async processing requires `webhook=true` and a configured Speech-to-Text webhook.
- Webhook HMAC verification must use the exact raw request body.
- The provider response remains an implementation detail in private R2. Spring receives
  only the provider-neutral normalized transcript contract.

Official references:

- [ElevenLabs create transcript API](https://elevenlabs.io/docs/api-reference/speech-to-text/convert)
- [ElevenLabs asynchronous Speech-to-Text webhooks](https://elevenlabs.io/docs/eleven-api/guides/how-to/speech-to-text/batch/webhooks)
- [ElevenLabs API pricing](https://elevenlabs.io/pricing/api?price.section=speech_to_text)

## Re-capture rule

Re-capture when the Scribe model, requested signal set, or response shape changes. Keep
the old fixture in Git history; never overwrite the current fixture with a dashboard
export containing request/account metadata.
