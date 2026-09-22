# Last session

## Current handoff — 22 September 2026 — claims integration RFC

The user requested a new PR to plan integration of the isolated claims-extraction
and Jev documentary-evidence experiments. Read the
[architecture proposal](docs/product/claims-evidence-integration-plan.md) and
[portable lab baseline](docs/product/claims-evidence-lab-baseline-2026-09-22.md).
This branch contains documentation only. No migration, application change, new
capability, provider inference, purchase or deployment is included.

The proposal extends Spring and the existing processing Worker with independent
optional jobs, immutable claim/document/evidence versions and explicit provider
attempt accounting. Missing Jev access does not block the first proposed slice:
private replay of a saved extraction tied to a frozen revision, with a source-minute
link and existing owner authorization. Implement that slice only in a subsequent
implementation PR; this RFC does not implement it or graduate any pilot gate.

The user explicitly confirmed Luna through the OpenAI API, using application API
credentials rather than the ChatGPT account/subscription. The proposed adapter uses
Responses and preserves Luna xhigh. Jev is TypeSafe AI's evaluator. The historical
extraction lab used Codex subscription access and needs reevaluation over the API.
Jev has 24 semantic fixtures, ten simulated failure scenarios and no real inference.
All 16 offline lab tests passed again on 22 September. The lab files are outside
this repository; their hashes and limitations are recorded in the baseline.

**Current operational state supersedes the 10 September entry below:** hosted
development was suspended on 21 September at the user's request for cost control.
The workspace shutdown record and current workspace instructions report the API
Container stopped, processor schedules and all three public/preview routes disabled,
and development deployment gates disabled. DB/R2/secrets/subscriptions remain.
Resume only on a new user request. The local suspension changes and evidence in the
original checkout are deliberately not folded into this design branch.

The branch was created from `origin/develop` at `a0b8cde` in an isolated worktree.
Both original application checkouts contain unrelated user changes and remain
untouched. Frontend implementation belongs to a later PR against its own repository.

Validation: all 85 API tests passed with local PostgreSQL 17/pgvector Testcontainers;
all 16 offline Jev lab tests passed. Local links in both new documents and Markdown
fences were checked; `git diff --check` passed. GitHub repository and development
environment deployment/processing gates were read and remain `false`. No hosted
endpoint was called. These checks validate the baseline and documentation, not an
implemented claims feature or Jev model quality.

## Historical handoff — 10 September 2026

**Development resumed and all eight capabilities are enabled by the user's explicit request.**
Read [the complete rollout evidence](docs/operations/capability-rollout-2026-09-10.md). The 8 September suspension below
is historical. Public development routes and processor Cron are active; previews
remain off. Production stays gated and no new payments or production resources
were created. Galician is the default and Spanish the only alternative.

Both integration PRs were merged into develop after passing GitHub verification.
Development deployment gates are re-enabled at repository/environment scope,
including asynchronous processing. The API deployment applies each image through
the authenticated development restart/readiness control. Final credential rotations
are reserved for the user; the existing operations credential is also installed in
the GitHub development environment. Never print ignored recovery material.

Verified: actual R2 upload, ElevenLabs/webhook transcription, duplicate/retry/abort
boundaries, Range playback, exception review, automatic agenda and cited guides,
anonymous publication, immutable correction, withdrawal and both search modes.
Galician public version 2 and Spanish public version 1 remain available.
Hybrid threshold 0.48 improves principal task success from 13/16 to 16/16; the
additional set improves 6/10 to 9/10. All ten no-answer cases are correct, no request
failed, and hybrid warm p95 is below 2.4 seconds. One specific reply remains outside
the top 20 on the additional set. These synthetic results do not graduate the real
institutional human-time, accessibility, load, restore or governance pilot.

Validation: 85 API, 45 processor and 309 frontend tests; Worker typechecks; builds;
4 search evaluator and 2 offline production configuration tests. Windows Docker
startup recovery preserves data volumes. Future production configuration is generated
offline with isolated resources and a pinned approved image; see the domain launch
annex. No domain, HA production database, sender or production credentials exist yet.
Keep Omarchy work separate. Review the remaining field-pilot gates before launch.

## Historical handoff — 8 September 2026

**Current override: hosted development is suspended at the user's request.**
Read [the suspension record](docs/operations/hosted-suspension-2026-09-08.md)
before the historical recovery notes below. Public/preview routes and Cron are
disabled; GitHub deployment gates are false. Do not resume automatically.
The [rollout record](docs/operations/capability-rollout-2026-09-08.md) records
V18 and provisioned resources; the July/early-September inventory below is historical.

**Updated:** 8 September 2026 (Europe/Madrid)

## Suspension handoff — latest user decision

- Local stack and Docker Desktop stopped. Do not restart automatically.
- Removed the processor's every-five-minute (`*/5 * * * *`) and daily
  (`17 3 * * *`) Cron schedules through the Cloudflare administration API.
  A subsequent live read returned `schedules: []` successfully.
- Committed and pushed development `triggers.crons: []` in
  `processing-worker/wrangler.jsonc`. Deploying the current configuration keeps
  schedules disabled; deploying an older configuration containing schedules
  could restore them. Removing the property is NOT equivalent to an empty list.
- Disabled public workers.dev and preview URLs for `subtitula-web-dev`,
  `subtitula-api-dev`, and `subtitula-processing-dev`; persisted development
  route settings in both repositories.
- Set `DEPLOY_ENABLED=false` at repository and development-environment scope
  in both repositories, and API `PROCESSING_DEPLOY_ENABLED=false` in both
  scopes. No active Actions or Workflow instances were observed at suspension.
- **Container shutdown is NOT confirmed:** the latest live check still showed
  `development-singleton` running. Its configured inactivity timeout is ten
  minutes; Cron removal can take up to fifteen minutes to propagate. These are
  not a guaranteed shutdown deadline. Verify actual state before claiming that
  container usage charges have stopped.
- Database, backup, R2 files, secrets and deployments are preserved. Workers
  Paid and PlanetScale subscriptions were NOT cancelled. The $5 Workers plan
  includes usage allowances, not a spending cap; an active container can incur
  overages even with public access disabled.

Resume only after an explicit user request: read the suspension and rollout
records, deliberately restore the required routes and schedules, verify runtime
capability flags after container restart, then enable deployment gates as needed.
Do not restore schedules or hosted services as a side effect of unrelated work.

## Start here

Read [the consolidated recovery and capability table](docs/operations/continuity-2026-09-08.md).
It identifies all three requested Claude conversations, the extra September handoff,
the two Windows clones, provider-key differences, the recovered commits and the next
safe step. It supersedes older handoff claims that the launcher fix is uncommitted or
that the PlanetScale extension gate has never been checked.

## Saved work

Both Windows clones were clean. Claude's September work was in
`C:/Users/Sejio/subtitula`, on local `develop`, ahead of the deployed remote.
This review recovered it into `C:/Users/Sejio/Dev/subtitula`, on
`feature/transparency-evidence-search-integrated` in both repositories:

- frontend `1d1f009`: IPv4 processor readiness probe and five-second timeout;
- frontend `ec287f4`: processor deployment dependency warning;
- API `de88ad8`: September continuity note;
- API `712d018`: deployment-order prerequisite.

The integration branches are the backup destination. No push to `develop` or
production, deployment, provider call, credential rotation or capability activation
is part of this recovery. The unintegrated transparency branch retains conflicting
migration numbers; keep using the integrated history with V11–V18.

Two previously unversioned artifacts now live under `docs/product/`:
`architecture-flows.md` and `subtitula-sala-de-control.html`. They describe the
implementation, not a live inventory of enabled services.

## Exact next step

Finish local durable-upload checks: abandon/clean up, reopen without IndexedDB,
and recover a retryable failure without uploading again. Read the local-vs-hosted
notes in the pilot runbook before involving ElevenLabs: local R2 is Miniflare,
whereas real source_url/webhook E2E requires a provider-accessible hosted sample.

Only the Claude Windows clone has `CAPABILITY_DURABLE_INSTITUTIONAL_UPLOAD=true`
in its ignored `.env`. Its provider keys are blank; the Dev clone has Google and
ElevenLabs values, but their validity is unverified. HMAC pairs match within each
clone. Never infer local configuration or data from Git branch state.

Hosted rollout remains pending. PlanetScale extensions and R2 account activation
were reported complete in September; backup, private bucket/CORS, processor,
Workflows, S3/provider/webhook secrets and the real smoke still need completion or
fresh verification. GitHub Account ID already exists; PROCESSING_DEPLOY_ENABLED
is absent. DEPLOY_ENABLED=true makes develop pushes deploy.

## Separate work: Omarchy

Navigation and screen inventory were reported integrated only in Linux `develop`
(20 commits ahead; 17 screens, 34 captures, 350 tests in that session). Coherence
triage remains pending. On 8 September the user explicitly deferred recovery of
that work and will upload it later if needed. It is NOT part of this backup.
Do not present the three remote design specs as the recovered implementation.

## Canonical references

- `docs/product/transparency-evidence-search-implementation-plan.md`
- `docs/operations/transparency-pilot-runbook.md`
- `docs/product/transparency-api-contract-v1.md`
- `docs/product/transparency-ux-acceptance.md`
- `docs/product/decision-log.md`

Human review stays exception-only, normally 2–5 active minutes of enrichment work.
The historical branch verification was API 81 tests, processor 28, frontend 301,
plus builds/typechecks/dry-runs. Those results do not certify hosted activation.
