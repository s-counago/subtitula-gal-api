# Last session

**Updated:** 30 July 2026 (Europe/Madrid)

**Repositories:** API `develop` at `0154633`; frontend `develop` at `613c52e`

**Snapshot branch:** `feature/transparency-evidence-search` in both repositories

**State:** committed feature-branch snapshot for remote safekeeping; `develop` and
production branches remain untouched and nothing was deployed.

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

Phases 0–7 of the canonical plan are implemented locally behind capabilities. Phase 8
pilot hardening is implemented locally; its hosted, real-data, governance, load, and
relevance gates remain open.

- Flyway V9–V16 add the normalized evidence model, durable ingestion, exception review,
  agenda/guide evidence, immutable publication snapshots, lexical search, 1024-dimension
  pgvector projections, and configured cost/search metrics.
- The processing Worker owns private R2 upload/download signing, durable ingest,
  enrichment, and publication-index Workflows, ElevenLabs Scribe v2 orchestration,
  Workers AI guide/embedding calls, idempotent signed API commands, retries, daily
  abandoned-upload cleanup, and 90-day privacy-safe analytics retention.
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

- Spring/Testcontainers: `.\mvnw.cmd -B test` — **78 tests passed**; PostgreSQL
  17 + pgvector applied all 16 migrations.
- Processing Worker: **23 tests passed** across 6 files.
- Search evaluator: **3 tests passed**; secret-bootstrap parser: **2 tests passed**.
- API container Worker and processing Worker typechecks passed.
- API container image build and both Cloudflare Worker deployment dry-runs passed with
  the expected Container, Workflows, R2, AI, service, and rate-limit bindings.
- Frontend: **186 tests passed** across 36 files; `tsc --noEmit`, Next.js/OpenNext
  production build, and Cloudflare deployment dry-run passed.
- `git diff --check` passed in both repositories.

The frontend test suite still prints the existing jsdom “navigation not implemented”
diagnostic; the suite exits successfully.

## External state and remaining gates

No Cloudflare resource, Worker Secret, PlanetScale extension/schema, hosted database,
provider webhook, or deployment was changed. The only remote mutation is the
`feature/transparency-evidence-search` snapshot branch in each GitHub repository.

Before any push to `develop` (which auto-deploys), follow the runbook. In particular:

1. Back up the PlanetScale development branch and prove `vector`, `unaccent`, and
   `pg_trgm` can be enabled by the migration role. V15 runs even while hybrid is dark.
2. Provision the private development R2 bucket and processor Workflows/service bindings;
   verify rate-limit namespace IDs `10001`–`10004` are unused.
3. Install the documented API/processor secrets without printing or reading them back.
4. Run a single synthetic/public hosted session with every new capability still false,
   then activate one slice at a time.
5. Build a governed real Galician/Spanish gold set, pre-agree relevance/no-answer,
   latency, workload, and margin thresholds, and graduate hybrid only if it clears them.
6. Complete accessibility, load, retention/export, DPA/subprocessor, RGPD/ENS, and
   backup/restore exercises before non-public pilot data.

## Exact next safe step

Treat the feature branch as a backup snapshot, not a deployable candidate yet. At snapshot
time it was based four API commits and seventeen frontend commits behind each
`origin/develop`. Reconcile those upstream changes carefully, rerun the entire
cross-repository verification matrix, and review the resulting diff before any PR or
`develop` push. Then, with explicit authorization for hosted changes, perform the
PlanetScale extension/pre-deploy gate and development backup described in the pilot
runbook before enabling any new capability.

Preserve unrelated pre-existing worktree edits, especially product/backlog documents and
workspace guidance, when splitting or committing this implementation.
