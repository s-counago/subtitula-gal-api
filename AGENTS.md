# Repository guide

This is the Spring Boot API repository. Its sibling frontend is `../subtitula-gal`; the workspace parent is not a Git repository. `develop` represents hosted development and `main` represents production. Deployment workflows currently run CI and select a GitHub Environment, but contain no real deployment command.

The canonical local launcher lives in the frontend repository: use `..\subtitula-gal\start-up.ps1` on native Windows, or `../subtitula-gal/start-up.sh` on Linux/WSL 2. Docker Desktop is required for PostgreSQL, Mailpit, and Testcontainers. Keep `mvnw` and shell scripts on LF line endings.

Configuration contracts:

- `local`: Docker PostgreSQL 17, SMTP to Mailpit on `localhost:1025`, non-Secure cookie.
- `dev`: Cloudflare Container at `subtitula-api-dev.<account-subdomain>.workers.dev`, isolated PlanetScale PostgreSQL dev branch, `EMAIL_PROVIDER=disabled`, `EMAIL_DELIVERY_REQUIRED=false`, Secure cookie. Email calls fail visibly; test email E2E locally with Mailpit.
- `prod`: the same Container image, isolated PlanetScale PostgreSQL production branch, Cloudflare Email Service prod token, `EMAIL_DELIVERY_REQUIRED=true`, Secure cookie. It remains gated until the custom-domain annex is activated. No hosted fallback providers.

`dev` and `prod` are profile labels that both activate the shared `hosted` profile from `application.yml`. Keep all hosted behavior in `application-hosted.yml`; the only differences between deployments are runtime environment variables and secrets. Do not branch Java code or configuration files by environment. CI/CD must promote the same immutable image digest from dev to production.

Copy `.env.example` to the ignored `.env` for local credentials. Never commit or paste secrets into docs, issues, chats, or workflows. Google credentials are for an OAuth Web application, not a generic API key. ElevenLabs needs a real restricted/free-tier key for transcription. Mailpit needs no key and sends no real email.

Cloudflare assigns each Worker `<worker-name>.<account-subdomain>.workers.dev`; no DNS zone or purchased domain is needed for hosted dev. The future custom domain and Cloudflare Email Service onboarding are documented in `docs/operations/custom-domain-launch-annex.md`. Never use `workers.dev` as `APP_EMAIL_FROM`: Cloudflare does not let accounts onboard that shared domain for sending.

Email verification is non-blocking: registration creates the session before attempting mail, all send failures are caught/logged, and no API feature checks `emailVerified`. Hosted dev intentionally leaves users unverified; its frontend must set `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false` so it does not promise undeliverable verification or reset messages.

The local database uses the explicitly versioned Compose volume `postgres17-data`. A PostgreSQL data directory cannot be reused across major versions without `pg_upgrade`; do not attach an old PostgreSQL 16 anonymous volume to 17 or delete an old volume until its data has been intentionally dumped/preserved. Local launchers must use bounded readiness checks and surface Compose/application logs when a service fails.

The API routes do not have an `/api` prefix. Health is `GET /ping`. Run `.\mvnw.cmd -B test` on Windows or `./mvnw -B test` on Linux/WSL 2 before handoff.

The target compute is Cloudflare Containers. Containers are not persistent VMs: all disk is ephemeral, so never run PostgreSQL, use R2/FUSE as a PostgreSQL data directory, or store uploads there. Spring connects directly over JDBC/TLS to PlanetScale PostgreSQL provisioned through Cloudflare. This intentionally preserves JPA, Flyway, Spring Session JDBC, PostgreSQL SQL, and Testcontainers parity. Hyperdrive is not used because it is a Workers-only accelerator for an existing database, not a JDBC endpoint for the Spring Container. Future durable files belong in R2.

Read `docs/operations/environment-contract.md`, `docs/operations/secrets-setup.md`, and `docs/operations/cloudflare-architecture.md` before changing deployment or runtime secrets.
