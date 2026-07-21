# Last session

- Standardized one codebase across local/dev/prod: PostgreSQL 17 locally and PlanetScale PostgreSQL hosted; Next.js Workers + Spring Containers.
- Local E2E uses Docker PostgreSQL, Mailpit, Google OAuth and ElevenLabs.
- Hosted dev will use free `workers.dev` URLs. Domain, production HA and Cloudflare Email Service are deferred.
- Dev email is non-blocking and hidden in the UI; local keeps email E2E enabled through Mailpit.
- Updated environment/secrets docs, `AGENTS.md`, dashboard, frontend flags and tests. Deployment workflows remain disabled placeholders.

## Next step

Implement Wrangler/OpenNext and Container manifests, provision only PlanetScale dev plus the secrets in `docs/operations/secrets-setup.md`, then deploy and run the hosted E2E checklist before setting `DEPLOY_ENABLED=true`.
