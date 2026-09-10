# Development capability rollout — 10 September 2026

The user explicitly resumed development and authorized the remaining capability
activation, existing services and credential management in Bitwarden. New
payments require separate authorization. Production and the domain annex remain
gated. This supersedes the 8 September suspension for development only.

## Verified state

Verified search runtime at 20:06 UTC: all eight capabilities true; the principal
development comparison passed and hybrid is retained with threshold `0.48`. API Worker
`e58cb417-6e64-4416-ab44-b3c77cc4531a`, immutable Spring image
`sha256:2645030385c88fc3b3c230eb97014b5f044f199b0f5795303fcdd814bfaeb5ab`;
processor `f0fecadc-b612-445c-ae4d-cb066c165ed8`; frontend
`515886c8-8ece-4ada-8c51-ef190b47ca4a`. Subsequent GitHub development deployments
use the same merged configuration; this identifies the image used for evaluation.
These are synthetic development smokes, not graduation of the full
human-time, relevance, load, accessibility or governance pilot.

- Reopened the three existing `workers.dev` routes. Preview URLs remain off.
- The suspended API instance was confirmed inactive before resumption. Its first
  resumed health check returned OK after 31.6 seconds.
- API Worker `643943bf-b9db-44ba-b4aa-55fd46bcf9e2` adds authenticated development
  lifecycle operations; deployed with `--containers-rollout none`, preserving
  the existing immutable Spring image. `/backend/capabilities` now returns
  durable upload, normalized transcript and exception review true. The later
  agenda deployment `f90cff28-d1d7-45cd-9646-875792c0f3d4` and a verified restart
  also enabled automatic agenda. Guide, publication and lexical were subsequently
  enabled sequentially after the preceding hosted smoke; hybrid was then enabled
  for the paired comparison after the indexed lexical baseline passed.
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
  defaults to Galego. API/frontend deployment and the two-option Chrome selector
  are verified; the second Spanish pilot submits and transcribes as `spa`.
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

- Spring/PostgreSQL: 85 tests passed, including both upload paths' language scope,
  idempotent starts, immutable guide entities and racing index dispatch.
- Processor: 45 tests passed, including lifecycle authorization, project language
  and name-warning regressions. Both Worker typechecks passed.
- Frontend: 309 tests and Next/OpenNext build passed; includes polling regression
  cases, immediate lexical indexing with hybrid disabled and completion status.
- API complete image/Worker/processor dry-run passed after the language changes.
- Processor `3e56f470-8699-4caf-9156-250138eab9b9` deployed the language/name
  fixes and restored `*/5 * * * *` and `17 3 * * *` schedules. Deployment output
  confirms both triggers and all three Workflow bindings.
- Review, agenda, guides and both language publications are verified; the labelled
  Galician/Spanish relevance/no-answer comparison passes for development hybrid.
  Automated synthetic smoke does not graduate human task-study,
  normal-length media, accessibility, load, restore or governance gates.
- Final credential rotations are reserved for the user after development, per
  their 10 September instruction. Retain ignored recovery material until the
  user's rotation/recovery check is complete; never commit or print it.
- Keep Omarchy compatibility work separate. Do not provision production or
  purchase a domain/plan. Production readiness follows the canonical launch annex.

## Hosted review, guide and publication evidence

- Primary Galician project `4ce077be-933e-480d-a953-3d44b6a2fe97`, recording
  `fa2d77f3-63e7-40ec-92e1-b0e6285c24b2`, ingest
  `cdb7391f-3542-4c92-8a43-bd7100d6abb5`: actual ElevenLabs/webhook success,
  14 Galician segments, 51.779 s, 3,165 micro-USD configured estimate.
  Omitting project language selected `glg`. Missing-object completion returned
  409; after the actual PUT, two completions returned the same job/recording.
- An independent uploaded cancellation fixture was aborted, then S3 HEAD returned
  404; the primary recording remained readable. A stale signed webhook returned
  401, and the original delivery replay returned duplicate=true without modifying
  the reviewed revision or text.
- Chrome required only the unknown-speaker decision. It remains unidentified.
  Optional name warnings did not block completion. No full transcript review or
  human timing study is claimed. Spanish original agenda-only enrichment succeeded
  in 12.57 s; Galician agenda+guide succeeded after hosted schema retries.
- First Galician guide `c65b63d5-f22e-41cf-bdda-4b6cf9784f23` has five cited topics
  and one explicitly evidenced water-pipe agreement, confirmed in Chrome. The
  negative library agreement was not promoted. The first successful guide needed
  roughly 16 minutes including retries, outside the desired latency; fixes are
  still being validated. A boundary one sentence early is corrected in
  `monotonic-anchors-v2` and a publication correction is in progress.
- Publication `5d972cbe-99b4-4b1f-aecf-0e07dffddf7b`, version 1, is public at
  `/transparencia/sesion-ficticia-en-galego-auga-e-biblioteca-proba-de-capacidades-4ce077be`.
  Anonymous DTO fields were checked for private-state leakage. Its returned,
  version-pinned media URL delivered HTTP 206 / 1,024 bytes. Lexical indexing job
  `236976d5-39e6-459b-abab-5b3ddff062b2` succeeded; `tubaxes` returns seven
  evidence-linked records while a correction is being prepared.
- Correct Spanish pilot `6408afb4-b3fb-4bb0-830a-531f29ff8def`, ingest
  `7e7e01da-6c5b-43c7-9c4d-ddf818206c67`, exposed a lost start response:
  Spring had committed but Workflow retried stale versions. Start now returns
  the already-committed transition without another event/attempt. Explicit 409
  JSON prevents Spring's protected error dispatch masking conflicts as 401.
  Both stale rejection and recovered versions were verified live. Restarting
  that Workflow from its start step completed with the same job, attempt 1 and
  preserved recording. Its no-agenda guide is being validated.
- Frontend updates the project status after review/publication/retry actions.
  It now offers durable-media access retry instead of claiming the recording
  exists only on this device. A direct processor deployment accidentally omitted
  the derived R2 endpoint; the canonical deployment wrapper restored it and
  signed playback was rechecked. Always use `npm run deploy:dev` in the processor
  with `CLOUDFLARE_ACCOUNT_ID`, never bare Wrangler deploy.
- Cloudflare's GLM now returns a chat-completion envelope; incomplete output is
  rejected. Runtime citation validation is retained and unsupported grammar
  `uniqueItems` is checked locally. The model's named JSON-schema contract and
  reasoning controls are under live verification. Primary references:
  [GLM contract](https://developers.cloudflare.com/workers-ai/models/glm-4.7-flash/)
  and [reasoning controls](https://github.com/cloudflare/ai/blob/main/packages/workers-ai-provider/README.md).

## Correction recovery and search preparation

- GLM `guide-v3` uses its named schema envelope and disables thinking. Four real
  Galician windows generated and validated in about 79 seconds; Spanish extraction
  completed successfully with three correctly separated topics, an explicit library
  agreement and no invented transport agreement. References remain linked to the
  actual frozen transcript, preserving unidentified voices.
- An unchanged-text correction exposed reused topic/contribution/decision IDs.
  IDs now include project, revision, generation settings and validated output.
  Spring rejects existing entity IDs before any write, retaining exact duplicate
  retry behavior. Regression checks cover all three entity types and preservation
  of a confirmed decision. ERROR dispatches retain their real status; a direct
  unauthenticated `/error` request is still protected.
- The blocked Galician Workflow was terminated, then its four existing real model
  artifacts were reassembled with the corrected deterministic IDs and persisted
  once through the signed internal API. No model or STT call was repeated for this
  recovery. Public version 1 was byte-for-byte equal before and after recovery.
- Galician publication version 2 is `2890b42f-ee7f-4ca6-81b4-945806bc68ea`, with
  a visible correction note. Spanish publication is
  `3055738d-4739-4437-95ea-430728522cd0`. Anonymous DTO and HTTP 206 / 1,024-byte
  source playback checks passed for both. Human confirmation was limited to the
  explicitly evidenced candidate agreement in each synthetic session.
- A first search run started before the five-minute scheduler had indexed the
  corrected Galician publication. It scored 3/16 and is retained as a readiness
  failure, not the relevance baseline. The UI now dispatches both lexical and
  semantic jobs immediately after publishing or rebuilding. Scheduler/browser
  races reuse the admitted Workflow rather than returning a spurious 503.
- Once both lexical indexes completed, the 22 preregistered cases yielded 13/16
  task success, all 6 no-answer cases correct, no HTTP failures, warm p95 1,174 ms.
  The evaluator now counts each distinct evidence reference once in nDCG, avoiding
  inflated credit from several result kinds citing the same segment. Original
  query labels are versioned in `scripts/fixtures/hosted-search-cases-2026-09-10.json`.
- Production preparation is executable offline with `scripts/prepare-production.mjs`:
  isolated production names/bindings, mandatory verified mail, same approved image
  digest and same-origin OAuth gateway; routes, schedules and capabilities remain
  disabled. Two isolation/digest/input tests pass. No resources or payments were
  created. See the updated custom-domain launch annex.

## Final search and withdrawal checks

The [recorded measurements](evidence/search-evaluation-2026-09-10.json) retain the
initial threshold attempts and final results. At `0.32`, hybrid found every expected
answer but admitted five absent topics. At `0.48`, those false matches disappeared.
Retrieval now also returns the actual evidence cited by a matching public topic,
so a short answer fragment does not lose the context supplied by its guide.
Every expanded result stays bound to the same active publication and its pinned
guide; ordinary public filters still apply. A regression test deliberately gives
the source fragment a nonmatching vector and checks its citation-based retrieval.

| Set | Mode | Task success | Correct no-answer | MRR | nDCG@20 | Warm p95 |
|---|---|---:|---:|---:|---:|---:|
| Principal: 16 answer + 6 absent | Lexical | 13/16 | 6/6 | 0.393 | 0.498 | 1,024 ms |
| Principal | Hybrid | 16/16 | 6/6 | 0.611 | 0.706 | 2,011 ms |
| Additional: 10 answer + 4 absent | Lexical | 6/10 | 4/4 | 0.250 | 0.345 | 1,051 ms |
| Additional | Hybrid | 9/10 | 4/4 | 0.388 | 0.497 | 2,391 ms |

There were no failed requests. All 36 final hybrid responses explicitly reported
HYBRID, not lexical fallback. The principal preregistered gain/no-answer/latency
criteria pass. The additional set was labelled after calibration but before its
first run. It exposed a lexical prefix defect (`plan` matching `plantea`), corrected
generally by treating words shorter than five characters as complete FTS terms.
Its final repeat is therefore additional regression evidence, not an untouched
holdout. Case `h10` still misses the specifically labelled reply in the first 20;
do not describe retrieval as universally correct or this set as representative.

The public evidence IDs are reproducible through the versioned JSONL fixtures:

```powershell
npm run evaluate:search -- --fixture scripts/fixtures/hosted-search-gold-2026-09-10.jsonl --origin https://subtitula-api-dev.s-counago00.workers.dev --mode lexical --delay-ms 150
npm run evaluate:search -- --fixture scripts/fixtures/hosted-search-gold-2026-09-10.jsonl --origin https://subtitula-web-dev.s-counago00.workers.dev --mode hybrid --delay-ms 150
```

The final measurements used the frontend gateway for both modes. Use
`hosted-search-additional-gold-2026-09-10.jsonl` for the additional set.

The earlier, separately authored synthetic Spanish session was published and
indexed solely to verify withdrawal. Both lexical and hybrid returned its evidence
before withdrawal and zero results afterward. Its public DTO and media-access route
then returned 404. The two primary publications still returned 200, and Galician
version 1 still exactly matched its saved public snapshot. No primary recording,
database or source artifact was removed.

Chrome also verified a Spanish paraphrase finding Galician water-pipe evidence.
Following the result opened publication version 2 and focused the correct source
at `00:11`, with its visible correction note and unidentified speaker preserved.

Both integration PRs were merged into `develop` after successful GitHub checks:
[API #1](https://github.com/s-counago/subtitula-gal-api/pull/1) and
[frontend #1](https://github.com/s-counago/subtitula-gal/pull/1). Development
repository/environment deployment gates are re-enabled, including the processor.
The API deployment uses the existing operations credential in its GitHub
development environment to apply the new image and verify actual readiness.
No credential was rotated for this: final rotations remain the user's responsibility.
Production workflows still perform no deployment and its gates/resources remain closed.

## Development container controls

From the API repository, use `node scripts/hosted-container.mjs` with `status`,
`suspend`, `resume` or `restart`. Supply `HOSTED_OPERATIONS_TOKEN` through the
environment or the ignored rollout file. Never put its value on a command line.
The client polls actual running state and checks that suspension rejects ordinary
requests. This controls only the API container; complete hosted suspension must
also disable processor schedules, public routes and GitHub deployment gates as
described in the 8 September suspension runbook. DB/R2 and fixed subscriptions
remain preserved.
