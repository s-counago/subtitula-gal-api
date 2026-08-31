# Last session

**Updated:** 31 August 2026 (Europe/Madrid)

**Integrated commits:** API `c4f09f4`; frontend `fa0eeb3`

**Remote branch:** `feature/transparency-evidence-search-integrated` in both repositories

**State:** the reviewed implementation, develop integration, hardening fixes, tests, and
documentation are committed and pushed to both remotes. `develop` and production
branches remain untouched and nothing was deployed.

## Canonical context

Start with:

1. `docs/product/transparency-evidence-search-implementation-plan.md`
2. `docs/operations/transparency-pilot-runbook.md`
3. `docs/product/transparency-api-contract-v1.md`
4. `docs/product/transparency-ux-acceptance.md`
5. `docs/product/decision-log.md`

The original product goal is an evidence-first transparency flow for plenary sessions:
durable video, a timestamped transcript, a concise structured guide, and literal plus
semantic search whose results always link back to source evidence. Human intervention
must stay exceptional and normally take 2–5 active minutes per session, never require a
line-by-line transcript or agenda review.

## Implementation state

Phases 0–7 of the canonical plan and the current Phase 8 hardening are implemented on
the remote integrated branches behind server-enforced capabilities. Hosted, real-data,
governance, load, and relevance gates remain open.

- Flyway V11–V18 add the normalized evidence model, durable ingestion, exception review,
  agenda/guide evidence, immutable publication snapshots, lexical search, 1024-dimension
  pgvector projections, and configured cost/search metrics.
- The processing Worker owns private R2 upload/download signing, durable ingest,
  enrichment, and publication-index Workflows, ElevenLabs Scribe v2 orchestration,
  Workers AI guide/embedding calls, idempotent signed API commands, retries, five-minute
  job/recording reconciliation, and 90-day privacy-safe analytics retention.
- Private recordings reopen through an owner check and a renewable 15-minute signed R2
  URL; local development retains an authenticated Range proxy. The Spring Container and
  database do not proxy hosted video bytes.
- R2 deletion is two-phase (`ABORTED/EXPIRED`, idempotent object delete, `DELETED`), so
  concurrent abort/cleanup cannot delete a verified recording and failed deletion stays
  retryable.
- Human review is exception-driven: low-confidence text, speaker, overlap, and ambiguous
  transition issues enter a short queue. Agenda and structured-guide candidates are
  generated and deterministically validated; editors can correct/freeze them, but no
  routine agenda-boundary or contribution-by-contribution approval step is required.
- Publication uses immutable source snapshots with correction notes, withdrawal, latest
  version restore, media Range/timestamp links, and an owner reindex path that preserves
  document identities/click attribution while clearing stale embeddings.
- Search supports exact phrases, PostgreSQL FTS, natural-keyword fallback, trigram typo
  tolerance, filters, and deterministic evidence boosts. Dark hybrid search embeds with
  BGE-M3, takes lexical and semantic top-100 candidates, and fuses them with RRF (`k=60`);
  every AI/internal failure falls back to lexical search.
- Search analytics store an HMAC and bounded metadata, never raw query text, IP, or user
  agent. The evaluation CLI reports Recall@20, task success@20, MRR, nDCG, no-answer
  quality, zero-result rate, and p50/p95 without emitting query text.
- The frontend includes institutional upload/reopen/retry, lifecycle status, exception
  review, agenda/guide correction, publication/correction/withdraw/reindex controls,
  public explorer and within-session search, evidence cards with timestamps/context, and
  a pilot panel for active review time, workload, latency, engagement, and configured
  gross cost estimates.

## Safe rollout state

`normalizedTranscript=true`. All later capabilities are deliberately `false`:

- `durableInstitutionalUpload`
- `exceptionReview`
- `automaticAgenda`
- `structuredGuide`
- `publicPublication`
- `lexicalSearch`
- `hybridSearch`

Do not enable several slices together. Enrichment or embedding failure must never
unpublish evidence or interrupt lexical search.

## Verification completed

- Spring/Testcontainers: `.\mvnw.cmd -B test` — **81 tests passed**; PostgreSQL
  17 + pgvector applied all 18 migrations.
- Processing Worker: **28 tests passed** across 6 files; strict typecheck passed.
- Search evaluator: **3 tests passed**; secret-bootstrap parser: **2 tests passed**.
- API container Worker and processing Worker typechecks passed.
- API container image build and both Cloudflare Worker deployment dry-runs passed with
  the expected Container, Workflows, R2, AI, service, and rate-limit bindings.
- Frontend: **301 tests passed** across 48 files; Next.js/OpenNext
  production build, and Cloudflare deployment dry-run passed.
- `git diff --check` passed in both repositories.

The frontend test suite still prints the existing jsdom “navigation not implemented”
diagnostic; the suite exits successfully.

## Qué falta realmente

No Cloudflare resource, Worker Secret, PlanetScale extension/schema, hosted database,
provider webhook, or deployment was changed. The integrated feature branch and all its
documentation are available remotely in both GitHub repositories.

The code path is implemented and locally verified. These are the remaining operational
prerequisites before treating hosted development as functional:

1. Back up the PlanetScale development branch and prove `vector`, `unaccent`, and
   `pg_trgm` can be enabled by the migration role. V17 runs even while hybrid is dark.
2. Provision/verify the private `subtitula-media-dev` bucket, processing Worker,
   Workflows, service bindings, and unused rate-limit namespace IDs `10001`–`10004`.
3. Install the documented ElevenLabs, internal-HMAC, analytics-HMAC (optional), and R2
   S3 credentials without printing or reading Worker Secrets back. The deploy derives
   `R2_S3_ENDPOINT` from `CLOUDFLARE_ACCOUNT_ID` and fails early if it is absent.
4. Apply and list `processing-worker/r2-cors.development.json` with
   `npm run r2:cors:dev:apply` and `npm run r2:cors:dev:list`; this session could not
   mutate the live bucket because it had no Cloudflare API token.
5. Run one synthetic/public hosted smoke with the new capabilities dark, including
   upload, webhook, server-side Workflow reconciliation, private reopen/playback,
   exception review, publication, lexical fallback, cleanup, and URL renewal. Activate
   one server capability at a time only after its smoke passes.
6. Keep hybrid search dark until a governed Galician/Spanish gold set clears agreed
   relevance, no-answer, p95 latency, workload, and cost/margin thresholds.
7. Complete long-session/load, accessibility, retention/export, DPA/subprocessor,
   RGPD/ENS, and backup/restore exercises before using non-public pilot data.
8. Organization membership and institutional role enforcement remain intentionally
   deferred. Personal ownership checks protect the current pilot; do not represent the
   organization entities as a completed authorization model.

## Exact next safe step

Open PRs from `feature/transparency-evidence-search-integrated` only after preparing the
PlanetScale backup/extensions and the required Cloudflare R2/Workflow/secrets/CORS
resources. A merge into `develop` auto-deploys, so follow the deployment order and smoke
procedure in `docs/operations/transparency-pilot-runbook.md`; keep every new capability
dark initially and graduate them one by one.

Preserve unrelated pre-existing worktree edits, especially product/backlog documents and
workspace guidance, when splitting or committing this implementation.
