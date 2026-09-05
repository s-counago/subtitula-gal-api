# Last session

**Updated:** 3 September 2026 (Europe/Madrid)

**Head commits:** API `develop` = `63cf00a`; frontend `develop` = `fa0eeb3`

**State:** the transparency work is merged into `develop` in **both repositories, local
only**. The API is 3 commits ahead of `origin/develop` and the frontend 2. Nothing has
been pushed and nothing has been deployed. `pgvector` and R2 are now enabled on the live
accounts; every other hosted resource is still absent.

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

## What this session changed

### Branch hygiene

Deleted five local branches whose commits were already in `develop`: `app-split`,
`flows-ux` and `segment-styling` in the frontend, `flows-ux` and `segment-styling` in the
API. All were removed with `git branch -d`, which refuses unmerged work.

The remote `feature/transparency-evidence-search` (the pre-merge branch) and
`feature/transparency-evidence-search-integrated` still exist in both repositories and
were **not** deleted.

### The merge

`feature/transparency-evidence-search-integrated` was a strict superset of `develop`, so
it merged as a **fast-forward** in both repositories with no conflicts:

- frontend `f4cbd42..fa0eeb3` — 40 files, +4,963 / −305
- API `b2409d0..63cf00a` — 302 files, +28,220 / −92

The integrated branch had already renumbered its Flyway migrations from `V9–V16` to
`V11–V18` to clear the collision with `V9__project_segments` and `V10__project_approval`
on `develop`. The merged sequence `V1`–`V18` is contiguous. Never merge the un-integrated
`feature/transparency-evidence-search`, which still carries the colliding numbers.

### Frontend `master` has diverged

Three documentation-only commits exist **only on `origin/master`** and never reached
`develop`, which is 25 commits ahead of them otherwise:

```
549626e docs: coherence pass design spec (subproject 3 of frontend coherence)
4a6671b docs: screen inventory tool design spec (subproject 2)
bd06856 docs: navigation shell design spec (subproject 1)
```

They add `docs/superpowers/specs/2026-08-04-*.md`. Decide deliberately whether to
cherry-pick them into `develop`. The API's `origin/main` is clean — fully contained in
`develop`.

### Deployment gates, verified live

- `DEPLOY_ENABLED=true` as a repository variable in **both** repositories. A push to
  `develop` therefore deploys immediately.
- `PROCESSING_DEPLOY_ENABLED` is **not defined**, so `deployment-gate.yml` skips
  `npm run deploy:processing:dev` and the processing Worker is not deployed by a push.
- **Push order matters.** The frontend `wrangler.jsonc` now declares
  `PROCESSING_SERVICE → subtitula-processing-dev`, a Worker that does not exist yet, so a
  frontend deploy is expected to fail until the processor exists. The route code treats
  the binding as optional (`PROCESSING_SERVICE?`) and degrades at runtime; the problem is
  deploy-time binding resolution only. Provision and deploy the processor first, then the
  frontend. A failed `wrangler deploy` is atomic and leaves the current frontend serving.

### Hosted prerequisites now satisfied

- **PlanetScale `development`:** `vector`, `unaccent` and `pg_trgm` are created. Verified
  with the application role, not just the admin role — `SELECT '[1,2,3]'::vector(3)`
  returns, so the extension is resolvable from `subtitula_app_dev`'s `search_path` and
  `V17` will not fail on the `vector(1024)` column. PlanetScale lists the extension under
  its product name *pgvector*; the SQL identifier is `vector`. No dashboard step is
  needed — that section only covers restart-requiring extensions such as `pg_cron`.
- **R2:** was not merely un-provisioned but **not enabled on the account at all**
  (`wrangler r2 bucket list` returned error 10042). It has since been enabled. The
  `subtitula-media-dev` bucket and its CORS policy are still to be created.

### Local development environment on this machine

This machine had never run the local stack; several pieces were bootstrapped:

- `processing-worker/node_modules` installed (`npm ci`). Its absence was the first
  failure; the launcher installs it automatically, so prefer `.\start-up.ps1` over
  starting the Worker by hand.
- `.env` did not exist and was created from `.env.example`. `processing-worker/.dev.vars`
  likewise. Both are ignored by git and share one generated
  `INTERNAL_API_HMAC_SECRET`; `scripts/prepare-local-secrets.mjs` aborts if they diverge.
  `ELEVENLABS_API_KEY` and the Google credentials are still blank, so there is no real
  transcription locally and the launcher substitutes dummy Google values.
- `CAPABILITY_DURABLE_INSTITUTIONAL_UPLOAD=true` **in local `.env` only**, to exercise the
  first slice. Hosted configuration is untouched.
- `wrangler login` was completed interactively (account `ccfcbd8c1bb4b863368b8d76f14baf03`).
  The stored OAuth token had expired, which blocked the processor entirely: the `AI`
  binding is remote-only, so `wrangler dev` needs credentials even for local work.
- PostgreSQL 17.11 client installed through winget for `psql`. The Windows service
  `postgresql-x64-17` was **stopped and disabled** so it cannot claim port 5432 from the
  Compose database. Re-enable with `Set-Service -StartupType Automatic` if a native
  server is ever wanted.

### Launcher fix — uncommitted

`start-up.ps1` probed `http://localhost:8787/ping` with `Invoke-WebRequest`. Wrangler
listens on IPv4 only, while .NET resolves `localhost` to `::1` first and stalls until the
timeout, so all 60 attempts failed against a healthy Worker. Measured side by side with
the Worker running: `127.0.0.1` answered **200 in 0.12 s**, `localhost` **failed at 2 s**.
The first request also costs about **2.26 s** (later ones ~0.2 s), above the old 2 s
allowance.

The probe now targets `127.0.0.1` with `-TimeoutSec 5` and carries a comment explaining
why, so it is not "corrected" back. `start-up.sh` needs no change: `curl` performs Happy
Eyeballs and falls back to IPv4 by itself — which is also why an early `curl` check
wrongly appeared to exonerate `localhost`.

**This edit is still uncommitted in the frontend worktree.** It is unrelated to the
transparency branch and deserves its own commit.

## Safe rollout state

Hosted `normalizedTranscript=true`; every later capability is deliberately `false` in
`wrangler.jsonc`:

- `durableInstitutionalUpload`
- `exceptionReview`
- `automaticAgenda`
- `structuredGuide`
- `publicPublication`
- `lexicalSearch`
- `hybridSearch`

The capability flags live in `wrangler.jsonc` vars, so flipping one is a commit and a
deploy, not a dashboard toggle: auditable and revertible, but with no emergency switch.
Disabled slices return **404, not 403**, so they are not discoverable as dark APIs, and
the frontend fails closed — `/capabilities` errors resolve to all-false.

Do not enable several slices together. Enrichment or embedding failure must never
unpublish evidence or interrupt lexical search.

## Verification

### Re-run this session, after the merge

- Frontend: `npm test` — **301 passed** across 48 files; `npm run build` succeeded with
  TypeScript clean, listing the new `/processing/[...path]` and `/transparencia/[slug]`
  routes.
- API: `.\mvnw.cmd -B test` — **81 passed**, `BUILD SUCCESS` in 43 s, with Flyway
  validating all 18 migrations on PostgreSQL 17.10.
- Local stack via `.\start-up.ps1`: ready in **16 s**, all four endpoints 200
  (API 8080, processor 8787, frontend 3000, Mailpit 8025). `/capabilities` returned
  `durableInstitutionalUpload: true` with all later slices false; the local database held
  `V1`–`V18` and the `vector`, `unaccent`, `pg_trgm` extensions.

Not re-run this session: deployment dry-runs, processing-Worker tests, and the search
evaluator. Those results below are from the branch's own verification.

### On the branch, previously

- Processing Worker: **28 tests** across 6 files; strict typecheck passed.
- Search evaluator: **3 tests**; secret-bootstrap parser: **2 tests**.
- API container image build and both Cloudflare deployment dry-runs passed with the
  expected Container, Workflows, R2, AI, service and rate-limit bindings.

The frontend suite still prints the pre-existing jsdom "navigation not implemented"
diagnostic; the suite exits successfully.

## Cloudflare audit at end of session

No resource was created by this session. Verified by read-only queries: no Workflows
deployed in the account; `subtitula-processing-dev` does not exist (error 10007);
`subtitula-api-dev` and `subtitula-web-dev` exist with their last deployments dated
**23 July 2026**, untouched. The only account contact was the ephemeral remote-proxy
session `wrangler dev` opens for the `AI` binding, and no model was ever invoked — local
traffic was `/ping` only.

The local stack is stopped, ports 3000/8080/8787/5432/8025 are free, no `workerd`,
wrangler or Spring process survives, and the `subtitula-gal-api_postgres17-data` volume is
preserved.

## Qué falta realmente

1. **Done:** PlanetScale extensions verified with the migration role. Still take a
   verifiable backup of the development branch before the first migrating deploy.
2. Create the private `subtitula-media-dev` bucket now that R2 is enabled, then apply and
   list its CORS policy with `npm run r2:cors:dev:apply` and `npm run r2:cors:dev:list`.
3. Confirm the processing Worker and Workflow names `subtitula-processing-dev`,
   `subtitula-ingest-dev`, `subtitula-enrich-dev`, `subtitula-index-dev`, and that
   rate-limit namespace IDs `10001`–`10004` are unused in the account.
4. Install the ElevenLabs key and webhook secret, the internal HMAC (identical in the API
   and the processor — a mismatch fails ingestion with an unhelpful signature error), the
   optional analytics HMAC, and the R2 S3 credentials. Never read Worker Secrets back.
   Set `CLOUDFLARE_ACCOUNT_ID` in the GitHub Environment: the processor deploy derives
   `R2_S3_ENDPOINT` from it and fails early without it.
5. Configure the ElevenLabs STT webhook toward the processor and record its ID in
   `ELEVENLABS_WEBHOOK_ID`, currently empty in `processing-worker/wrangler.jsonc`.
6. Set `PROCESSING_DEPLOY_ENABLED=true`, push the API first, then the frontend once the
   processor exists. Run one synthetic/public hosted smoke with every new capability dark.
7. Activate one server capability at a time, starting with `durable upload`, and verify
   its cleanup after the grace period before moving on.
8. Keep hybrid search dark until a governed Galician/Spanish gold set clears agreed
   relevance, no-answer, p95 latency, workload, and cost/margin thresholds.
9. Complete long-session/load, accessibility, retention/export, DPA/subprocessor,
   RGPD/ENS, and backup/restore exercises before using non-public pilot data.
10. Organization membership and institutional role enforcement remain intentionally
    deferred. Personal ownership checks protect the current pilot; do not represent the
    organization entities as a completed authorization model.

## Exact next safe step

Finish local phase 0 in the browser before provisioning anything else, with the local
stack running and `durableInstitutionalUpload` true locally:

- create an upload intent and abandon it; the file must never reach Spring, and cleanup
  must remove it after `UPLOAD_CLEANUP_GRACE_SECONDS`;
- close the browser after the PUT and reopen the editor on the same device and on another
  without an IndexedDB copy;
- force a retryable provider failure and confirm "Tentar de novo" does not re-upload.

R2 is simulated locally by miniflare and local media is served through an authenticated
Range proxy rather than signed URLs, so blank R2 S3 credentials do not block any of this.

Then commit the `start-up.ps1` fix separately, and only afterwards begin the hosted
provisioning above, following `docs/operations/transparency-pilot-runbook.md`.
