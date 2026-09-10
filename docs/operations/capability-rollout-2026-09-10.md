# Development capability rollout — 10 September 2026

The user explicitly resumed development and authorized the remaining capability
activation, existing services and credential management in Bitwarden. New
payments require separate authorization. Production and the domain annex remain
gated. This supersedes the 8 September suspension for development only.

## Verified state

- Reopened the three existing `workers.dev` routes. Preview URLs remain off.
- The suspended API instance was confirmed inactive before resumption. Its first
  resumed health check returned OK after 31.6 seconds.
- API Worker `643943bf-b9db-44ba-b4aa-55fd46bcf9e2` adds authenticated development
  lifecycle operations; deployed with `--containers-rollout none`, preserving
  the existing immutable Spring image. `/backend/capabilities` now returns
  durable upload, normalized transcript and exception review true. The later
  agenda deployment `f90cff28-d1d7-45cd-9646-875792c0f3d4` and a verified restart
  also enabled automatic agenda. Guide, publication, lexical and hybrid remain false.
- `HOSTED_OPERATIONS_TOKEN` is installed as a dedicated API Worker Secret and
  saved in Bitwarden item `Subtitula — Development container operations`, ID
  `b2482aed-8bce-4650-91be-b4c101260a24`. It is not passed to Spring. No existing
  database, Google or processor credential was extracted from Worker Secrets.
- During Bitwarden verification the original operations token appeared as
  separate characters in tool output, bypassing whole-string redaction. It was
  replaced immediately: the previous value now returns 401, the replacement
  returns 200, and the Bitwarden recovery value was compared successfully without
  emitting it. Do not print revealed credential DOM/snapshots, even with simple
  string substitution. The user subsequently asked to leave final credential
  rotations to them after development; no additional rotations are requested now.
- Saved processor recovery JSON in Bitwarden item `Subtitula — Processing
  development runtime secrets`, ID `62e6a1e7-debc-4590-b619-b4c10129b6a2`.
  Its password field contains the five installed development secret names/values.
  The item exists; a full recovery round-trip comparison remains pending. This
  bundle was not exposed in verification output.
- Lifecycle verification: status reads did not start Spring; suspension returned
  `running:false`, regular `/ping` returned 503 without starting it, and explicit
  resumption returned healthy in 27.1 seconds. A running-instance SIGTERM cycle
  subsequently confirmed shutdown and resumed in 28.2 seconds. An immediate
  post-deployment restart first returned transient 500 and retained the old flag;
  a later restart applied agenda correctly. Always check runtime `/capabilities`,
  not only Worker settings or `/ping`. The client now retries transient readiness
  failures without restarting again just because one health request failed.
- GitHub repository/development deployment gates remain false in both repos;
  API processing gate remains false while the integration branches are validated.

## Real provider and browser evidence

- Self-authored fictional Spanish speech, generated offline with Windows Speech;
  source `scripts/fixtures/hosted-pilot.es.txt`. No real person/institution or
  private recording. WAV: 2,333,324 bytes, SHA-256
  `67ed30e395c6f2462574f5a6e6e77770548f631c17f18a780ab7148071e62619`.
- Project `c0c3a714-4d15-4ea6-b9b1-a65767d3c3bf`; recording
  `81ae06cd-4e93-41f8-a6e8-57c0ce5b107f`; ingest job
  `a110ab86-ca01-4c8f-bddc-6b5267d0b0c3`; Workflow
  `ingest-d1728958-b3c1-4fda-8fed-1cb77bbdaaf6` completed.
- The real ElevenLabs submission and webhook produced 14 timed segments,
  52.139 seconds, one unknown speaker. Job attempt 1 succeeded in 15.322 seconds;
  configured cost estimate 3,187 micro-USD (not an invoice).
- Signed R2 playback returned 206 for bytes 0–1023 of the complete recording.
  A fresh Chrome login reopened the editor with 00:52 media and the exception
  queue, without a browser-local copy from the upload.
- Review showed one required unknown-speaker issue and six optional name
  warnings. Sentence starters generated five false positives. The processor
  fix suppresses common Spanish/Galician starters while retaining unfamiliar
  and accented names; two regression cases pass.
- The project requested Spanish but the old processor always submitted its
  configured `glg` hint. It now uses the authorized job's project language.
  The user clarified that the product is Galician-first with Spanish as its only
  alternative. New code defaults explicitly to `glg`, supports only `glg`/`spa`,
  and rejects other input before the provider call. A two-option upload selector
  defaults to Galego. API/frontend deployment of this restriction is next.
  Existing transcript metadata is historical and is not silently rewritten.

## Docker recovery

Docker Desktop 4.49.0 / engine 28.5.1 repeatedly failed on stale Windows AF_UNIX
files in both `%LOCALAPPDATA%/Docker/run/dockerInference` and
`%LOCALAPPDATA%/docker-secrets-engine/engine.sock`. Disabling inference alone did
not prevent the listeners. With Docker stopped, both temporary parent directories
were preserved under unique `*-before-subtitula-*` names, then Docker restarted.

The frontend `start-docker.ps1` now runs from `start-up.ps1`. It uses bounded
Docker CLI calls, refuses directory recovery while the backend is running,
validates paths and rejects unexpected secrets-directory contents. Model Runner
stays disabled. No Docker reset, WSL change or volume removal. A full stopped
start and a subsequent healthy no-op both passed; `postgres17-data` remains.
This recovers project startup; it does not patch Docker Desktop's own launcher.

## Checks and remaining work

- Spring/PostgreSQL: 83 tests passed, including both upload paths' language scope.
- Processor: 39 tests passed, including lifecycle authorization, project language
  and name-warning regressions. Both Worker typechecks passed.
- Frontend: 302 tests, Next build and OpenNext deployment dry-run passed.
- API complete image/Worker/processor dry-run passed after the language changes.
- Processor `3e56f470-8699-4caf-9156-250138eab9b9` deployed the language/name
  fixes and restored `*/5 * * * *` and `17 3 * * *` schedules. Deployment output
  confirms both triggers and all three Workflow bindings.
- Continue review completion, automatic agenda, structured guide and publication
  sequentially, then a labelled Galician/Spanish relevance/no-answer comparison
  before hybrid. Automated synthetic smoke does not graduate human task-study,
  normal-length media, accessibility, load, restore or governance gates.
- Final credential rotations are reserved for the user after development, per
  their 10 September instruction. Retain ignored recovery material until the
  user's rotation/recovery check is complete; never commit or print it.
- Keep Omarchy compatibility work separate. Do not provision production or
  purchase a domain/plan. Production readiness follows the canonical launch annex.

## Development container controls

From the API repository, use `node scripts/hosted-container.mjs` with `status`,
`suspend`, `resume` or `restart`. Supply `HOSTED_OPERATIONS_TOKEN` through the
environment or the ignored rollout file. Never put its value on a command line.
The client polls actual running state and checks that suspension rejects ordinary
requests. This controls only the API container; complete hosted suspension must
also disable processor schedules, public routes and GitHub deployment gates as
described in the 8 September suspension runbook. DB/R2 and fixed subscriptions
remain preserved.
