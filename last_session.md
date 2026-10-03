# Last session

## Current handoff — 21 September 2026

Hosted development is suspended at the user's explicit request for cost control.
The API returned suspended=true/running=false; processor schedules, all three
public/preview routes and GitHub deployment gates are disabled and verified.
DB/R2/secrets and subscriptions are preserved. Resume only on a new user request.
Read [the shutdown record](docs/operations/hosted-suspension-2026-09-21.md).
Manifest changes remain local/uncommitted. Billing review awaits Cloudflare
browser sign-in; infrastructure OAuth cannot read billing history (HTTP 403).

## Current handoff — 11 September 2026

**Latest discussion — claim identity, context and repeatable experiments:** the user
prefers approach 2 (bounded low-cost LLM plus validation), starting with existing GLM,
and prioritizes avoiding incorrect data. Read the [clarifications and experiment ideas](docs/product/afirmaciones-aclaraciones-y-ensayos-2026-09-11.md).
Claims and occurrences have separate identities; evidence remains mandatory; further
authorized transcript context can be retrieved; structured proposals pass application
validation before persistence/public acceptance. DeepSeek-V4.1-Flash direct-API prices
and cache semantics were checked; no inference calls or new provider purchases occurred.
Document/news verification is explicitly deferred in [the future scope](docs/product/afirmaciones-cotejo-documental-futuro.md)
and backlog CLAIM-DOC-01. The 45k-token session remains an illustrative assumption,
not a measurement. Next: discuss the proposed corpus and repeatable small experiments;
no automatic continuous processing or implementation is started. Earlier work is preserved.

**Latest follow-up — exploratory report, before implementation planning:** the user
requested an explanatory document about persistent speaker profiles, explicit claims,
a shared catalogue of matters, repeated statements and evidenced contradictions.
Read [the exploration report](docs/product/afirmaciones-asuntos-informe-exploratorio-2026-09-11.md).
It explains a worked fictional example, candidate retrieval versus matter identity,
uncertainty, catalogue deduplication, model options, hypothetical costs and experiments.
Official sources/prices checked on 11 September; no claim/matter model evaluation,
application change, deployment or new provider purchase. The decision log records this
as exploration only. Next: user review and discussion before an implementation plan.
Natural-language search remains pending; all documentation changes remain local and
uncommitted, preserving the previous diagram/search documentation follow-ups.

**Previous follow-up — natural-language search remains incomplete:** the user's exact
question `quién habló de las pérdidas de las tuberías del agua?` returned HTTP 200 /
HYBRID / zero results despite relevant evidence. Short Spanish topic queries returned
seven search documents. There is no dedicated “who” grouping, and the synthetic
speaker remains unidentified. See the [diagnostic and next development slice](docs/product/natural-language-speaker-search-2026-09-10.md).
NLS-01–NLS-06 are proposed acceptance criteria, not implemented or passing tests.
The next step is query-intent/topic handling, source retrieval and speaker grouping,
with bilingual, multi-speaker and no-answer evaluation. This follow-up changes only
documentation; capability activation stays complete, production remains gated.

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

### Functionality diagrams — 10 September 2026

The [interactive control room](docs/product/subtitula-sala-de-control.html#caps)
now opens on the eight-capability overview, with an individual explanation and
clickable navigation for each function. The [Mermaid diagrams](docs/product/architecture-flows.md)
show the end-to-end user flow and current development evidence. Stale resource/flag,
language, guide and publication-index readiness descriptions were corrected; the
canonical plan's phase table now distinguishes synthetic dev validation from pending
institutional field gates. Frontend README and historical UX spec link to these shared
diagrams. No application runtime, hosting, resource or credential changed.

Document checks exercised all 9 channels / 66 steps, matched all eight capability
names to the API contract, checked navigation, play/pause, node selection and local
links without script errors. The browser URL policy blocked opening the local HTML,
so visual layout inspection was not completed; checks used the document DOM locally.

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
