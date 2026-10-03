# Hosted development suspended — 21 September 2026

The user requested shutdown to stop unattended development compute costs.
This supersedes the development resumption of 10 September. Resume only on a
new user request. Production remains gated.

## Applied and verified

- `node scripts/hosted-container.mjs suspend` returned
  `{"suspended":true,"running":false}`. The client also verified HTTP 503 for
  ordinary requests and confirmed that the probe did not restart Spring.
- A second status read, after disabling processor/frontend triggers and before
  closing the API public route, still returned suspended and not running.
- Cloudflare reports zero processor schedules. The previous expressions were
  `*/5 * * * *` and `17 3 * * *`.
- Cloudflare reports `enabled:false` and `previews_enabled:false` for
  `subtitula-web-dev`, `subtitula-api-dev` and `subtitula-processing-dev`.
  All three public origins returned HTTP 404 after the change.
- GitHub `DEPLOY_ENABLED=false` was set and read back at repository and
  `development` environment scope in both repositories. API
  `PROCESSING_DEPLOY_ENABLED=false` was also set and verified at both scopes.
- The latest ten Actions runs in each repository were completed.
- The three Workflow listings contained only terminal instances: four ingest,
  seven enrichment and seven indexing instances. No queued or running instance
  appeared. Historical completed, errored and terminated instances were retained.
- Changes were applied with `wrangler triggers deploy --env development`, using
  the corresponding manifest for each Worker. No application image or Worker
  code version was deployed.

The three local development manifests now keep public routes disabled and the
processor manifest contains `triggers.crons: []`. These edits are local and
uncommitted; GitHub gates protect the currently published branches from automatic
deployment. An older manifest can restore routes/schedules if deliberately
deployed. Existing unrelated worktree changes were preserved.

## Preserved resources

PlanetScale, R2 objects, secrets, images, deployments and Workflow definitions
were preserved. No subscription was cancelled. Local services were outside this
hosted shutdown; no local server was started. Container application state
`ready` and a provisioned instance count are not evidence that Spring is running.
The provider's later container application read reported zero active, assigned
or starting instances. R2 contains 35 objects totaling 7.34 MB in Standard storage.

## Scheduled maintenance

Cloudflare Cron Triggers invoke `scheduled()` in
`processing-worker/src/index.ts`, on the `subtitula-processing-dev` Worker.
They are configured in `processing-worker/wrangler.jsonc` under
`env.development.triggers.crons`.

- Every five minutes: expire abandoned upload intents after the configured
  one-hour grace period, remove R2 recordings already authorized for deletion,
  confirm those deletions, and start missing ingestion/enrichment/indexing
  Workflow instances for pending jobs.
- Daily at 03:17 UTC: perform the same maintenance and remove search analytics
  older than the configured 90-day retention period.

Both schedules call Spring through `API_SERVICE`, even to discover that there
is no pending work. Five-minute polling is shorter than the API container's
ten-minute idle timeout and can prevent normal scale-to-zero. Explicit
container suspension rejects those calls before Spring starts.

## Billing review

The infrastructure OAuth credential cannot read billing history: the official
account billing endpoint returned HTTP 403. The browser required Cloudflare
sign-in, which was requested from the user. No last-cycle invoice amount has
yet been verified.

The existing resource contract estimates a retained base of USD 5/month for
Workers Paid and approximately USD 5/month for the PlanetScale PS-5 single-node
development cluster, before taxes and any storage/other usage. This is a
provisional estimate, not an account invoice or a promise of zero usage charges.
Charges already accrued in the current cycle remain payable after suspension.

The retained project's R2 storage is below the 10 GB-month Standard free
allowance, provided other account usage does not exhaust that shared allowance.
Published price references checked on 21 September:
[Workers Paid](https://developers.cloudflare.com/workers/platform/pricing/),
[Containers](https://developers.cloudflare.com/containers/platform/pricing/),
[PlanetScale single-node](https://planetscale.com/changelog/single-node),
[R2](https://developers.cloudflare.com/r2/pricing/).

## Resuming later

Restore the API development route deliberately before using the current
`hosted-container.mjs status|resume` client: it reaches that public route and
will now receive HTTP 404 while it is disabled. Preserve the operations token;
never print it or pass it as a command argument.

Reassess scheduled maintenance cadence before restoring Cron Triggers. Restore
frontend/processor routes and GitHub deployment gates only for intended work.
The API deployment workflow executes `hosted-container.mjs restart`, which
explicitly clears suspension and wakes Spring.
