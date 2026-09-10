# Last session

## Current handoff — 10 September 2026

**Development resumed by explicit user request. The suspension below is historical.**
Read [the current rollout evidence](docs/operations/capability-rollout-2026-09-10.md).
The three public development routes are restored. Production remains gated and
new payments require explicit authorization. GitHub deployment gates remain false
while both `feature/transparency-evidence-search-integrated` branches are validated.

Real R2 upload, ElevenLabs/webhook transcription, Range playback and a fresh Chrome
editor login are verified. Runtime capabilities: durable upload, normalized
transcript, exception review and automatic agenda true; four downstream capabilities false.
API Worker `643943bf-b9db-44ba-b4aa-55fd46bcf9e2` adds authenticated development
container controls. Its secret is installed and backed up in Bitwarden.
Windows Docker recovery is integrated in the frontend launcher; stopped-start,
healthy repeat passed. Current language-scope changes pass 83 API, 39 processor
and 302 frontend tests plus builds/dry-runs; deploy is next. Product scope is
Galician by default and Spanish only, without other languages or auto-selection.

Next: deploy the two-language changes, finish the synthetic review and test agenda,
guide, publication and search in order. A synthetic smoke does not prove the
normal-session human-time/relevance/load/governance pilot gates. Keep Omarchy separate.
The user will perform final credential rotations after development. The operations
token was already replaced after verification-output exposure; the prior value is
confirmed invalid and its replacement matches the saved Bitwarden recovery copy.

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
