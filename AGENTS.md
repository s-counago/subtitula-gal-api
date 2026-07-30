# Repository guide

This is the Spring Boot API repository. Its sibling frontend is `../subtitula-gal`; the workspace parent is not a Git repository. `develop` represents hosted development and `main` represents production. The `develop` workflow deploys the API Worker and Spring Container when the GitHub development variable `DEPLOY_ENABLED` is `true`; production remains gated.

The canonical local launcher lives in the frontend repository: use `..\subtitula-gal\start-up.ps1` on native Windows, or `../subtitula-gal/start-up.sh` on Linux/WSL 2. Docker Desktop is required for PostgreSQL, Mailpit, and Testcontainers. Keep `mvnw` and shell scripts on LF line endings.

Configuration contracts:

- `local`: Docker PostgreSQL 17, SMTP to Mailpit on `localhost:1025`, non-Secure cookie.
- `dev`: Cloudflare Container at `subtitula-api-dev.<account-subdomain>.workers.dev`, isolated PlanetScale PostgreSQL dev branch, `EMAIL_PROVIDER=disabled`, `EMAIL_DELIVERY_REQUIRED=false`, Secure cookie. Email calls fail visibly; test email E2E locally with Mailpit.
- `prod`: the same Container image, isolated PlanetScale PostgreSQL production branch, Cloudflare Email Service prod token, `EMAIL_DELIVERY_REQUIRED=true`, Secure cookie. It remains gated until the custom-domain annex is activated. No hosted fallback providers.

`dev` and `prod` are profile labels that both activate the shared `hosted` profile from `application.yml`. Keep all hosted behavior in `application-hosted.yml`; the only differences between deployments are runtime environment variables and secrets. Do not branch Java code or configuration files by environment. CI/CD must promote the same immutable image digest from dev to production.

Current hosted data state: PlanetScale database `subtitula`, default branch `development`, PS-5 single-node in `gcp-europe-west1` (Belgium), billed through Cloudflare. PlanetScale must keep the default branch technically production-capable, but the application reserves it exclusively for development and synthetic data. Role `subtitula_app_dev` is live and its current password exists only as a Cloudflare Worker Secret; all short-lived GitHub bootstrap copies were deleted after deployment. Reset it again if a human-managed Bitwarden recovery copy is required. Production is deliberately absent and must later use a separate HA branch/database and role.

The verified Windows CLI is `%LOCALAPPDATA%\Programs\PlanetScaleCLI\pscale.exe`. `pscale role get/reset` requires the opaque ID from `role list`, not `subtitula_app_dev`; suppress command output unless it is being transferred directly into the approved secret stores because create/reset responses contain the password.

Copy `.env.example` to the ignored `.env` for local credentials. Never commit or paste secrets into docs, issues, chats, or workflows. Google credentials are for an OAuth Web application, not a generic API key. ElevenLabs needs a real restricted/free-tier key for transcription. Mailpit needs no key and sends no real email.

Cloudflare assigns each Worker `<worker-name>.<account-subdomain>.workers.dev`; no DNS zone or purchased domain is needed for hosted dev. The future custom domain and Cloudflare Email Service onboarding are documented in `docs/operations/custom-domain-launch-annex.md`. Never use `workers.dev` as `APP_EMAIL_FROM`: Cloudflare does not let accounts onboard that shared domain for sending.

`workers.dev` is on the Public Suffix List, so the frontend and API worker hostnames are different browser sites. Hosted frontend traffic therefore uses the same-origin `/backend/*` gateway and strips that prefix before forwarding to Spring; Spring routes themselves remain rooted at `/ping`, `/register`, etc. The frontend calls this Worker through its `API_SERVICE` service binding; an ordinary same-zone public `fetch()` fails with Cloudflare error 1042. Build hosted dev with `NEXT_PUBLIC_API_URL=/backend`. Keep the gateway streaming request/response bodies and preserving `Set-Cookie`. Google OAuth uses `GOOGLE_REDIRECT_URI` to return through the exact hosted callback `https://subtitula-web-dev.s-counago00.workers.dev/backend/login/oauth2/code/google`; build dev with `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=true`.

The active Google clients are currently named `subtitula-local-current` and `subtitula-development-current`. Other same-environment clients in Google Cloud are historical, unused copies. Client names do not select credentials; compare client IDs before deleting anything.

The first API deployment may receive a Bitwarden Send and the freshly reset PlanetScale password through short-lived GitHub Environment bootstrap secrets. Bitwarden is hosted in the EU: native CLI automation must run `bw config server https://vault.bitwarden.eu` before `bw receive`; otherwise the CLI can return non-payload text with a successful status. Use the exact documented field names. Prefer consuming the Send locally once, installing local values with `npm run install:local-secrets`, and placing only the dev JSON in `BOOTSTRAP_SERVICE_SECRETS_JSON`. The workflow uploads dev values to Cloudflare Worker Secrets, after which all `BOOTSTRAP_*` secrets must be deleted from GitHub. Never install Bitwarden CLI from npm; use the checksummed native release pinned in the workflow. Docker is required both for tests and for Wrangler to build the Container image.

GitHub evaluates a job-level `if:` before loading its named Environment, so an Environment variable cannot gate that job. `DEPLOY_ENABLED` is intentionally a non-secret repository variable; Cloudflare tokens, bootstrap values, and runtime credentials remain Environment/Worker secrets.

The dev Container scales to zero. A measured cold `/ping` took about 24 seconds, while a warm service-bound request took under one second; health checks and E2E clients need a bounded cold-start allowance.

Linux Surefire orders integration-test classes differently from Windows. Security tests that intentionally share the same `@TestPropertySource` can otherwise reuse a mutated cached Spring context; the CSRF cookie contract test is deliberately dirtied before its class. Preserve that isolation unless the shared-context mutation is removed and both full-suite orders are verified.

Email verification is non-blocking: registration creates the session before attempting mail, all send failures are caught/logged, and no API feature checks `emailVerified`. Hosted dev intentionally leaves users unverified; its frontend must set `NEXT_PUBLIC_EMAIL_DELIVERY_ENABLED=false` so it does not promise undeliverable verification or reset messages.

The local database uses the explicitly versioned Compose volume `postgres17-data`. A PostgreSQL data directory cannot be reused across major versions without `pg_upgrade`; do not attach an old PostgreSQL 16 anonymous volume to 17 or delete an old volume until its data has been intentionally dumped/preserved. Local launchers must use bounded readiness checks and surface Compose/application logs when a service fails.

The API routes do not have an `/api` prefix. Health is `GET /ping`. Run `.\mvnw.cmd -B test` on Windows or `./mvnw -B test` on Linux/WSL 2 before handoff.

The target compute is Cloudflare Containers. Containers are not persistent VMs: all disk is ephemeral, so never run PostgreSQL, use R2/FUSE as a PostgreSQL data directory, or store uploads there. Spring connects directly over JDBC/TLS to PlanetScale PostgreSQL provisioned through Cloudflare. This intentionally preserves JPA, Flyway, Spring Session JDBC, PostgreSQL SQL, and Testcontainers parity. Hyperdrive is not used because it is a Workers-only accelerator for an existing database, not a JDBC endpoint for the Spring Container. Future durable files belong in R2.

Read `last_session.md` for the concise current handoff. Read `docs/operations/environment-contract.md`, `docs/operations/secrets-setup.md`, and `docs/operations/cloudflare-architecture.md` before changing deployment or runtime secrets.

Before changing institutional sessions, durable media, transcript review, agenda
alignment, structured guides, publication, public transparency, or search, read
`docs/product/transparency-evidence-search-implementation-plan.md` completely. It is the
canonical cross-repository plan; its minimal-human-review constraint and phase gates
supersede older plan fragments.
