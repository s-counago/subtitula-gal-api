# Hosted development suspended — 8 September 2026

The user explicitly requested suspension to avoid unattended usage charges.
This supersedes earlier activation instructions and historical statements that
`develop` pushes deploy automatically. Resume only on a new user request.

## Applied and read back from the providers

- Removed both processor Cron triggers (`*/5 * * * *`, `17 3 * * *`).
  Cloudflare reports zero scheduled triggers.
- Disabled `workers.dev` and preview URLs for `subtitula-web-dev`,
  `subtitula-api-dev`, and `subtitula-processing-dev`. The API reports both
  switches false for every Worker; all three public endpoints return 404.
- Set GitHub `DEPLOY_ENABLED=false` at repository and `development` environment
  scope in both repositories. Also set API `PROCESSING_DEPLOY_ENABLED=false`
  in both scopes. Neither repository has an in-progress Actions run.
- All three Workflows have zero instances: no queued or running transcription,
  enrichment, or indexing jobs.
- Updated development manifests to preserve route/Cron suspension on future
  deliberate deployments. No Worker code or database was deleted.

The API Container is configured to sleep after ten minutes without requests.
With external routes and Cron triggers disabled, it can become inactive.
The latest live check still reported `development-singleton` as `running`:
container shutdown is NOT confirmed. Cron removal can take up to fifteen minutes
to propagate, but this and the idle timeout do not guarantee a shutdown deadline.
Verify its actual state before reporting container compute charges stopped.

## Preserved resources and billing limits

PlanetScale database `subtitula` / branch `development` (PS-5), its backup and
schema V18 remain intact. R2 bucket, Worker secrets, images, deployments and
Workflow definitions are retained for resumption. Local services and Docker
remain stopped; no new local server was started for this suspension.

Suspension is not subscription cancellation and does not mean a zero invoice.
Workers Paid has a $5/month account minimum and the configured PlanetScale
single-node cluster is approximately $5/month, before tax and usage/storage
overages or other account services. PlanetScale does not offer voluntary
customer-initiated database sleeping. Removing its subscription would require
an explicit separate decision and a verified portable backup before deletion.

Sources: [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/),
[Container pricing](https://developers.cloudflare.com/containers/platform/pricing/),
[PlanetScale pricing](https://planetscale.com/docs/postgres/pricing),
[PlanetScale sleeping](https://planetscale.com/docs/plans/database-sleeping).

## Resumption checklist (not authorization)

Read the saved rollout report first. Restore development routes only when
intentionally restarting service, verify API capability flags after restarting
the existing container, and restore processor schedules only when needed.
Re-enable GitHub deployment gates only with explicit resumption authorization.
Do not overwrite secrets, reset databases, or merge the Omarchy rehearsal as
part of resuming this deployment.
