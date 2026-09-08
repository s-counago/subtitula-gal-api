# Development capability activation — 8 September 2026

In progress. User authorized activation of the remaining capabilities, account
access and required development infrastructure. Production remains gated.

## Provisioned and verified

- Private R2 bucket `subtitula-media-dev`, Western Europe location hint.
  Repository CORS applied; public `r2.dev` access disabled. A temporary synthetic
  object passed PUT and signed GET Range (206); the test object was removed.
- Bucket-scoped Object Read & Write S3 token `subtitula-media-dev-presign`.
- ElevenLabs development key from Bitwarden: enabled, Speech to Text access,
  5,000-credit refresh-period limit. A `/v1/user` 401 is `missing_permissions`
  (`user_read`), not evidence of expiration. Real transcription remains pending.
- Transcription-completed webhook `408c5225da5340f69e3ed7ae7ff1405b`, targeting
  `https://subtitula-processing-dev.s-counago00.workers.dev/webhooks/elevenlabs/speech-to-text`.
- Processor deployed as version `5c8b1eed-8a9f-48ca-921f-f48e939d1b1d`;
  `/ping` returns `status: ok`. Three Workflows and both scheduled triggers exist.
- All five processor secrets installed. Matching internal HMAC and development
  ElevenLabs key installed on the API Worker. Existing DB/Google secrets retained.
- PlanetScale backup `capabilities-rollout-20260908` / `10yur4e45rnd` succeeded,
  8,970,501 bytes, expires 8 October 2026. Live database still at V10 when checked;
  `vector 0.8.5`, `unaccent 1.1`, `pg_trgm 1.6` present.

## Verification so far

- API: 81 tests passed against Docker PostgreSQL/pgvector.
- Processor: 28 tests passed; search evaluation harness: 3 tests passed.
- Worker typechecks and processor deployment dry-run passed.
- These checks do not establish real transcription, guide quality, publication,
  search relevance, or completion of the canonical pilot gates.

## Windows Docker recovery

User requested Docker without inference. Disabled `EnableInference`,
`EnableInferenceTCP`, `EnableInferenceGPUVariant` in Docker's settings store,
preserving the previous file with suffix `.before-subtitula-20260908`.
The startup failure was a stale `Docker/run/dockerInference` socket. Preserved
the temporary runtime directory as `run-before-subtitula-20260908` and restarted.
Engine 28.5.1 responds and Testcontainers works. No database volumes were removed.

## Remaining

Deploy the API schema with flags dark, verify migration and service bindings,
then deploy frontend. Activate/test the capabilities sequentially under the
canonical implementation plan and pilot runbook, including real short-clip
transcription, exception review, agenda, guide, publication, lexical search and
the labelled relevance/no-answer comparison before enabling hybrid search.
Save new credential recovery copies securely and remove temporary rollout files
after installation/recovery verification. No secrets belong in this document.
