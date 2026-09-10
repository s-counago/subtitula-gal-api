# Transparency evidence and search — canonical implementation plan

**Status:** implementation complete through the dark hybrid baseline; pilot
hardening and hosted graduation gates remain

**Created:** 29 July 2026

**Scope:** `subtitula-gal`, `subtitula-gal-api`, and a processing Worker contained in the API repository

**Supersedes:** any interpretation of `product-refactor.md` that requires two exhaustive human review passes

This is the canonical cross-session implementation plan for turning an institutional
project into a durable, public, searchable plenary session. It combines:

- durable video/audio ingestion;
- versioned transcripts and timestamped evidence;
- speaker-aware, exception-only human review;
- automatic agenda alignment;
- an evidence-linked structured session guide;
- immutable publication snapshots;
- literal, filtered, fuzzy, and semantic search;
- public session and archive experiences; and
- the Cloudflare services, security, observability, tests, and rollout controls needed
  to operate the flow.

The central product constraint is:

> The enrichment flow must normally add no more than 2–5 minutes of human work to a
> plenary session after text/speaker review. Ten minutes is a failure threshold that
> requires simplifying or improving automation.

The central trust constraint is:

> Every concise claim must resolve to a speaker, transcript evidence segment,
> timestamp, publication revision, and—where applicable—an official document.

## 1. Mandatory context for every future session

Before implementing a task from this plan:

1. Read the workspace `AGENTS.md`, the relevant repository `AGENTS.md`, and
   `last_session.md`.
2. Read this plan completely, then the current phase row in section 18.
3. Read the canonical operations runbooks before changing hosting, bindings, secrets,
   database resources, or deployment:
   - `docs/operations/environment-contract.md`
   - `docs/operations/secrets-setup.md`
   - `docs/operations/cloudflare-architecture.md`
4. Inspect both worktrees. Preserve unrelated or uncommitted changes.
5. Work only on the next unblocked vertical slice. Do not jump directly to vectors,
   generative answers, or public publication.
6. Run the verification gates listed for the slice.
7. Update:
   - the phase status table in section 18;
   - the changelog in section 21;
   - `subtitula-gal-api/last_session.md`; and
   - the workspace-root `last_session.md`.
8. Record new architectural/product decisions in `decision-log.md`; do not silently
   change this plan.

No secret value, real transcript containing non-public information, signed URL, API
token, or database credential belongs in Git, documentation, logs, fixtures, or chat.

## 2. Desired user outcomes

### Institutional operator

An operator uploads or references a plenary session, leaves while it processes, then
returns to one editor. The editor shows only material exceptions in context. Once the
operator finishes required text/speaker corrections, Subtitula automatically organizes
the session. The operator may confirm a small number of ambiguous agenda transitions or
consequential decisions, then publishes.

### Citizen, journalist, association, or opposition member

A public user searches in ordinary language or with exact terms, receives evidence-first
results, jumps to the relevant second of the recording, sees who spoke and under which
agenda item, and opens related official documents. No login is required.

### Product/business

The system records processing cost, human review time, publication time, search success,
and evidence usage. The workflow is sellable because automation absorbs enrichment work
instead of shifting it to municipal staff.

## 3. Explicit non-goals

Transcription is Galician-first, defaulting explicitly to `glg`. Spanish (`spa`)
is the only optional alternative, confirmed by the user on 10 September 2026.
Do not add other transcription languages or automatic language selection.

- Do not replace the official minutes, agreements, electronic office, or document
  management system.
- Do not call an automatic guide an official `acta`.
- Do not classify ideology, political alignment, sentiment, intent, truthfulness, or
  speaker character.
- Do not publish an uncited generative answer as the primary search interface.
- Do not require manual assignment of every transcript segment to agenda items.
- Do not introduce D1, Durable Objects, Kafka, a separate vector database, or a second
  Spring API for the first implementation.
- Do not index drafts, private projects, working transcript revisions, private
  attachments, or withdrawn publications.
- Do not provision production infrastructure before the existing launch annex permits it.

## 4. Current baseline and gaps

The current product already has the correct single-project/editor direction, but:

- `Project.words` is one JSONB word array.
- uploads are buffered in Spring and discarded after synchronous transcription;
- the durable video exists only in browser IndexedDB;
- the ElevenLabs mapper discards diarization and other useful signals;
- institutional speakers and agenda inputs are local UI state;
- the public transparency explorer is demo data with `String.includes`;
- there is no transcript/publication version boundary;
- there are no public session APIs or durable media URLs; and
- there is no lexical or semantic search index.

Legacy creator projects must continue opening and exporting without forced migration.

## 5. Target architecture

```text
Browser
  |
  |-- /backend/* ----------------------> Web Worker --API_SERVICE--> Spring Container
  |
  |-- /processing/* -------------------> Web Worker --PROCESSING_SERVICE-->
  |                                                         Processing Worker
  |                                                         |  R2 binding
  |                                                         |  Workflows bindings
  |                                                         |  Workers AI binding
  |                                                         |  ElevenLabs HTTPS
  |                                                         `--API_SERVICE--> Spring
  |
  `-- signed PUT/GET -------------------------------------------------------> R2 S3

Spring Container ---------------- JDBC/TLS ----------------> PlanetScale PostgreSQL
```

### Repository placement

Keep the existing two repositories. Add the Worker under the API repository:

```text
subtitula-gal-api/
  processing-worker/
    package.json
    tsconfig.json
    wrangler.jsonc
    src/
      index.ts
      auth/
      providers/
      schemas/
      workflows/
        ingest-session.ts
        enrich-session.ts
        index-publication.ts
      observability/
    test/
```

The Worker is an independently deployed Cloudflare service, but it shares the API
repository, CI, review, operational documentation, and release branch.

### Responsibility boundary

**Spring owns:**

- authentication, CSRF, organizations, roles, and project authorization;
- the canonical relational model and Flyway migrations;
- transcript, speaker, agenda, guide, document, and publication state;
- review mutations and audit events;
- publication policy;
- public session APIs;
- lexical/vector database queries and final ranking; and
- analytics persistence and retention.

**The processing Worker owns:**

- R2 object access and signed upload/read URLs;
- Workflow orchestration and retries;
- ElevenLabs submission and webhook verification;
- Workers AI structured extraction and embeddings;
- provider-specific payloads;
- deterministic validation of generated payloads before API submission; and
- structured provider/job telemetry.

**The frontend owns:**

- the single upload/editor/public-search experience;
- progress and exception presentation;
- optimistic review interactions;
- publication preparation and preview; and
- evidence-first navigation and playback.

## 6. Infrastructure and environment contract

### Required additions

| Resource | Development | Production |
|---|---|---|
| Processing Worker | `subtitula-processing-dev` | not provisioned until launch annex |
| Private R2 media bucket | isolated dev bucket, synthetic/public test media only | isolated prod bucket |
| Ingest Workflow | development binding | future prod binding |
| Enrichment Workflow | development binding | future prod binding |
| Index Workflow | development binding | future prod binding |
| Workers AI | binding on processing Worker | isolated usage/budget controls |
| API service binding | processing → `subtitula-api-dev` | future prod API |
| Processing service binding | web → `subtitula-processing-dev` | future prod processor |
| ElevenLabs webhook | processing Worker HTTPS callback | future custom-domain callback |

### Not required initially

- Vectorize;
- AI Search;
- Queues;
- R2 event notifications;
- Hyperdrive in the Spring database path;
- a public R2 bucket;
- Cloudflare Stream;
- WebSockets or Durable Objects.

Add Queues only when indexing fan-out or throughput proves that Workflow instance
creation needs buffering. Benchmark AI Search/Vectorize only after the PostgreSQL hybrid
baseline exists.

### Local development contract

The complete local launcher remains in `subtitula-gal`, but Phase 2 extends it:

- Spring stays on `:8080`, Next stays on `:3000`, PostgreSQL stays on `:5432`, and
  Mailpit stays on `:1025`/`:8025`;
- the processing Worker runs through Wrangler on `:8787`;
- Next uses `PROCESSING_ORIGIN=http://localhost:8787` when a service binding is not
  available, matching the existing `API_ORIGIN` fallback pattern;
- Wrangler's local R2 persistence is kept under ignored local state;
- the local upload adapter streams small/fixture uploads into the R2 binding while hosted
  dev returns an S3 presigned URL; both return the same upload-intent response shape;
- local automated/provider tests use the sanitized recorded Scribe response by default;
- the real ElevenLabs `source_url`/webhook E2E runs in hosted dev because the provider
  cannot retrieve a localhost object;
- launcher readiness remains bounded and prints processor logs on failure;
- stop commands terminate the processor and preserve its local object state unless an
  explicit, verified cleanup is requested.

At Phase 7, local PostgreSQL remains major version 17 but moves to a verified image that
contains pgvector. Preserve the versioned `postgres17-data` volume and validate extension
availability before changing the image; do not delete or remount the volume blindly.

### Secrets

Processing Worker secrets are isolated per environment:

- `ELEVENLABS_API_KEY`
- `ELEVENLABS_WEBHOOK_SECRET`
- `INTERNAL_API_HMAC_SECRET`
- `R2_S3_ACCESS_KEY_ID`
- `R2_S3_SECRET_ACCESS_KEY`

Non-secret configuration belongs in `wrangler.jsonc`: API service binding, bucket
binding/name, account/bucket endpoint identifiers, provider model IDs, upload limits,
and feature defaults. Generate `Env` types with `wrangler types`; do not hand-write them.

The internal API secret signs method, canonical path, timestamp, nonce, and body digest.
Spring rejects expired timestamps, repeated nonces, invalid digests, and unsigned
internal routes. Public requests cannot use internal endpoints.

### Worker production rules

- new Worker uses a current compatibility date and `nodejs_compat`;
- `wrangler.jsonc`, not a new TOML file;
- bindings instead of Cloudflare REST APIs from inside the Worker;
- R2 objects and provider payloads are streamed or stored by reference, never buffered
  without an explicit small limit;
- every Promise is awaited or tracked;
- Workflow step names and branches are deterministic;
- Workflow params/returns contain IDs and R2 keys, never large transcripts;
- provider calls are idempotent and use stable job IDs;
- logs/traces are enabled and structured;
- no transcript text, signed URL, cookie, authorization header, or secret is logged;
- local unit tests use the Workers Vitest pool; hosted dev performs the real provider
  smoke test.

## 7. Canonical domain model

`Project` remains the aggregate root. Institutional projects gain normalized child
entities. Names below are conceptual table names; each migration must choose consistent
SQL and Java naming.

### Organization and authorization

`organizations`

- `id`, `name`, `slug`, branding, retention policy, created/updated timestamps.

`organization_members`

- organization, user, role (`owner`, `publisher`, `reviewer`), state.

Do not build full enterprise role management before the pilot, but do not make published
sessions depend forever on a single personal user ID.

### Project and recording

Extend `projects` with:

- optional `organization_id`;
- lifecycle `status`;
- session date, body/organ, location, session type;
- optimistic `version`;
- failure summary safe for UI;
- archived timestamp.

`recordings`

- project;
- opaque R2 object key;
- optional original/source URL;
- original filename stored separately and never used as the object key;
- MIME type, size, duration, checksum/eTag;
- upload state;
- usage permission and visibility;
- provider-safe source status;
- created/verified/deleted timestamps.

R2 key pattern:

```text
{environment}/organizations/{organizationId}/projects/{projectId}/
  recordings/{recordingId}/original
```

No human name, session title, email, or municipality name belongs in the object key.

### Transcript and evidence

`transcript_revisions`

- project, version number, parent revision;
- source (`asr`, `human_edit`, `correction`);
- provider/model/language and prompt/keyterm version;
- state (`working`, `frozen`, `superseded`);
- raw provider artifact R2 key and content hash;
- created by/at, frozen by/at.

`speakers`

- project;
- provider label (`speaker_0`);
- display label;
- optional confirmed name and role;
- identity state (`unknown`, `labelled`, `confirmed`);
- source and audit timestamps.

`evidence_segments`

- transcript revision and stable sequence;
- start/end milliseconds;
- speaker;
- original ASR text and current reviewed text;
- word timing JSON for subtitle/playback precision;
- agenda item if aligned;
- review state;
- signals JSON;
- optimistic version.

Evidence segmentation rules:

- never cross a confirmed speaker boundary;
- never cross an agenda boundary;
- prefer sentence/punctuation boundaries;
- split very long turns into readable passages;
- merge very short adjacent utterances only for the same speaker and continuous context;
- retain start/end timing and underlying word IDs.

`transcript_edit_events`

- revision, segment, actor, timestamp, prior/new content hashes, edit kind.

Do not log full before/after text in general application logs. The audit table is
authorized domain data.

### Review issues

`review_issues`

- project, transcript revision, optional segment/speaker;
- type;
- severity (`required`, `warning`);
- machine-readable signals;
- state (`open`, `resolved`, `dismissed`);
- resolution and actor/timestamps.

Initial issue types:

- unknown/changed speaker;
- low-confidence or provider-disagreement span;
- probable proper name/toponym;
- glossary/keyterm mismatch;
- overlap/noise/audio event;
- missing timing;
- invalid/empty segment.

Never display a fabricated “accuracy percentage.” Display the concrete reason to inspect.

### Agenda and alignment

`agenda_items`

- project, ordinal, external identifier, title, description;
- source (`manual`, `paste`, `document_import`, `official_url`);
- optional official document;
- visibility.

`agenda_alignments`

- transcript revision and agenda item;
- start/end segment IDs;
- signals;
- state (`automatic`, `confirmed`, `adjusted`, `unresolved`);
- whether a human check is required;
- model/algorithm version.

Alignment must be monotonic by default. An operator may explicitly mark an item as
revisited later without corrupting the main order.

### Structured session guide

The public label is “Guía da sesión” or “Resumo asistido con fontes,” never “official
minutes.”

`session_guides`

- project, transcript revision, guide version and schema version;
- generation model/prompt version;
- state (`generated`, `exceptions`, `ready`, `published`, `superseded`);
- generator content hash;
- created/confirmed timestamps.

`topics`

- guide;
- title, neutral summary, aliases;
- optional agenda item;
- start/end segment;
- generation state.

`contributions`

- topic, speaker;
- kind (`question`, `proposal`, `explanation`, `reply`, `objection`, `support`,
  `procedural`, `other`);
- neutral summary;
- generation state.

Do not infer “support” or “objection” unless explicit wording supports it; otherwise use
`other` or omit the classification.

`decisions`

- topic/agenda item;
- neutral description;
- status (`candidate`, `confirmed`, `document_supported`, `omitted`);
- optional motion/result/vote fields only when explicitly available;
- confirmer and timestamps.

`evidence_links`

- subject type/id (`topic`, `contribution`, `decision`);
- evidence segment;
- link purpose and ordering.

Every topic summary, contribution, and displayed decision requires at least one valid
evidence link. Decisions require explicit outcome language, a confirming human, or an
official supporting document.

### Documents and publication

`project_documents`

- project, type (`agenda`, `notice`, `minutes`, `proposal`, `agreement`, `other`);
- official URL and/or private R2 key;
- title, issuing body, document date, checksum;
- visibility and provenance;
- publication permission.

`publications`

- project and organization;
- immutable public slug;
- pinned frozen transcript revision;
- pinned guide version;
- pinned recording and visible document set;
- state (`draft`, `published`, `superseded`, `withdrawn`);
- version, responsible publisher, timestamps, correction note.

A correction forks a new working transcript/guide and creates a new publication version.
It never silently changes the evidence behind an existing publication.

### Search and processing

`search_documents`

- publication;
- kind (`evidence`, `topic`, `contribution`, `document_chunk`);
- source entity and evidence segment IDs;
- display text and normalized search text;
- weighted `tsvector`;
- optional embedding and embedding model/version;
- filter columns: organization, organ, session date, speaker, agenda item, language;
- start/end milliseconds;
- content hash and index version;
- active/tombstoned state.

`processing_jobs`

- project, type (`ingest`, `enrich`, `index`, `reindex`, `delete`);
- Workflow instance ID and provider request ID;
- state, current stage, attempt counts;
- input/output hashes and R2 artifact keys;
- model/prompt/index versions;
- started/completed timestamps;
- bounded safe error code/message;
- usage and cost fields.

`processing_events`

- job, stage, status, duration, provider usage, correlation ID, timestamp.

## 8. State machines

### Institutional project

```text
draft
  -> uploading
  -> uploaded
  -> transcribing
  -> review_required
  -> enriching
  -> ready
  -> published
  -> archived
```

Any asynchronous state may enter `processing_failed`, retaining the last successful
artifact and a retry action. A retry resumes from the failed idempotent stage.

Creator projects retain the existing behavior until migrated behind the same adapters.

### Human gates

There is one required human gate:

```text
review_required = resolve required text/name/speaker issues
```

After that:

- agenda alignment and guide generation are automatic;
- automatic topics/contributions can publish with citations and a clear assisted label;
- ambiguous agenda transitions are optional quick checks;
- unconfirmed consequential decisions are omitted or labelled as possible;
- enrichment exceptions do not block searchable transcript publication unless required
  metadata, legal permission, or evidence integrity is missing.

### Two output levels

**Searchable session**

- durable recording/source;
- frozen reviewed transcript;
- speaker labels;
- timestamps;
- public metadata and documents;
- literal/global search.

**Enriched session**

- agenda navigation;
- topic guide;
- speaker contributions;
- confirmed/document-supported outcomes;
- semantic search projection.

The first level is a valid publication. The second is an enhancement, not an operational
burden.

## 9. Upload and ingestion pipeline

### Upload UX/API

1. `POST /processing/upload-intents` with project ID, filename, size, MIME type, checksum
   when available.
2. Processing Worker verifies the authenticated user's project access through Spring.
3. It returns an opaque recording ID, allowed headers, expiry, object key token, and
   presigned R2 `PUT`.
4. Browser uploads directly to R2 and shows true byte progress where the browser API
   permits it.
5. Browser calls `POST /processing/upload-intents/{id}/complete`.
6. Worker verifies the R2 object with `HEAD`, size/type/checksum policy, then Spring moves
   the project to `uploaded`.
7. Worker creates an ingest Workflow with a stable instance ID derived from the job ID.

Initial hosted limit: reject files at or above the current provider `source_url` limit
with a useful pre-upload message. Record a backlog item for large-session audio
extraction/transcoding rather than accepting an upload that cannot be transcribed.

Abandoned upload intents expire and their unreferenced objects are removed by a bounded
cleanup job after the retention window.

### Ingest Workflow

Persist only IDs and R2 keys between steps:

1. validate job/project state;
2. create a time-limited signed R2 `GET`;
3. submit ElevenLabs Scribe asynchronously with:
   - `source_url`;
   - `scribe_v2`;
   - language/keyterms where validated;
   - diarization;
   - webhook enabled;
   - job/workflow correlation metadata;
4. store provider request ID in Spring;
5. wait for a small `transcription_complete` event;
6. webhook handler verifies the provider signature, writes the raw payload to R2, and
   sends the Workflow only the artifact key and digest;
7. normalize the response into revision/speaker/segment commands;
8. Spring transactionally creates the ASR working revision and review issues;
9. job becomes complete and project becomes `review_required`.

The webhook acknowledges only after the raw payload is durably stored. Repeated valid
webhooks are idempotent by provider request ID and payload digest.

## 10. Minimal human review UX

The institutional editor remains the only work surface.

### Entry state

While processing, show:

- current plain-language stage;
- last successful update;
- permission to leave the page;
- no fake percentage after upload unless the provider supplies measurable progress;
- bounded retry/help action on failure.

### Exception queue

The session panel starts with:

```text
12 cousas por comprobar
  4 nomes
  3 cambios de voz
  3 treitos con audio difícil
  2 avisos
```

Selecting an issue:

- seeks the video to the passage;
- highlights the relevant words;
- shows the current speaker and neighboring context;
- offers the smallest action: edit, assign, merge, confirm, dismiss;
- advances to the next issue without returning to a list.

Required issues block “Finish transcript review.” Warnings do not. The user may still
open the complete transcript and search within it, but is never required to read it
linearly.

### Review-time instrumentation

Record:

- first review open;
- active review intervals, excluding long idle periods;
- issues resolved/dismissed;
- manual edits outside surfaced issues;
- finish-review timestamp.

Store aggregate durations and counts, not keystrokes or video-watching surveillance.

Success target:

- text/speaker review improves materially against a linear editor;
- enrichment after this gate normally adds 2–5 minutes;
- sessions exceeding 10 enrichment minutes are sampled and investigated.

## 11. Automatic agenda alignment

Agenda import is optional and never blocks transcript publication.

### Inputs

- ordered agenda item titles/descriptions;
- reviewed evidence segments;
- explicit transition phrases;
- pauses and speaker changes;
- lexical similarity;
- multilingual embedding similarity when enabled;
- known session conventions per organization.

### Algorithm

1. Normalize titles and evidence text without destroying the display text.
2. Detect explicit anchors such as item numbers and transition phrases.
3. Score candidate start segments for every agenda item.
4. Apply a monotonic sequence alignment/dynamic-programming pass with minimum-span and
   non-overlap constraints.
5. Permit explicitly modelled “revisited item” ranges separately.
6. Produce signals, not a user-facing confidence percentage.
7. Mark only missing, overlapping, weak, or contradictory transitions for quick review.

### UX

- Show the agenda beside the timeline with automatic ranges.
- Normal items are collapsed and labelled “Organized automatically.”
- Ambiguous items appear in a short “3 quick checks” queue.
- A check opens a narrow timeline window with previous/current/next agenda titles.
- The operator chooses the correct transition segment or marks “not identifiable.”
- Never require dragging every boundary or assigning each segment.

Target for a normal 10–15 item session: 1–3 minutes; 5–10 minutes is a messy-case alert,
not the expected workflow.

## 12. Structured guide generation and validation

Trigger the enrichment Workflow when the required transcript review finishes.

### Hierarchical generation

Do not send an entire multi-hour transcript as one prompt.

1. Build bounded sections from agenda-aligned ranges; otherwise use deterministic,
   speaker-aware time/topic windows.
2. Provide segment IDs, timestamps, confirmed speaker labels, agenda metadata, and
   relevant visible document metadata.
3. Treat all transcript/document text as untrusted data, never as instructions.
4. Request JSON Schema-constrained output:
   - topic title/aliases/neutral summary;
   - contribution speaker/kind/neutral summary;
   - candidate decisions;
   - evidence segment IDs for every object.
5. Generate sections independently with stable prompt/model versions.
6. Consolidate adjacent duplicate topics deterministically or in a separately versioned
   bounded step.
7. Store the raw generated artifact in private R2 for reproducibility, then submit the
   validated command to Spring.

### Deterministic validator

Reject or downgrade any object when:

- an evidence ID is absent or belongs to another revision/project;
- a cited speaker differs from the contribution speaker;
- a time range does not contain its evidence;
- evidence is empty, private, or outside the publication candidate;
- a summary has no evidence;
- a decision lacks explicit outcome language, confirmation, or official-document support;
- output violates the schema or allowed neutral vocabulary;
- the provider appears to follow instructions embedded in transcript text.

Validation results:

- valid topic/contribution: save automatically;
- weak optional topic/contribution: omit;
- ambiguous agenda range: quick-check exception;
- consequential decision: request one-click confirmation or omit;
- invalid payload: retry bounded generation, then finish without enrichment rather than
  blocking searchable publication.

### Enrichment UX

The publication panel shows a summary, not a second editor:

```text
✓ 8 temas organizados con fontes
✓ 23 intervencións ligadas ao vídeo
! 2 posibles acordos por confirmar
! 1 cambio da orde do día pouco claro
```

Every preview item has “View evidence,” which opens the exact transcript/video context.
The operator confirms, edits the neutral label, or omits the item. There is no requirement
to approve all automatically generated topics or contributions.

## 13. Publication and corrections

### Publication checklist

Required:

- organization/body, title, date, language;
- permission and public recording/source;
- frozen reviewed transcript;
- required speaker/name issues resolved or explicitly left unknown;
- public slug;
- visible document provenance;
- responsible publisher.

Optional/enhanced:

- agenda alignment;
- guide topics/contributions;
- confirmed outcomes.

### Publish transaction

1. Spring freezes the working transcript revision.
2. It creates an immutable publication pinning revision, recording, guide, and documents.
3. It creates lexical `search_documents` transactionally.
4. It starts an index Workflow for embeddings.
5. The public session becomes available immediately with lexical search; semantic
   readiness is a non-blocking capability.

If indexing fails, the publication stays accessible and searchable lexically. Retry
indexing independently.

### Corrections

- “Correct session” forks the latest frozen revision into a new working revision.
- Operator edits and resolves new issues.
- Re-enrichment is content-hash incremental.
- Publishing creates version N+1 and supersedes N.
- Stable session URL shows the latest active version and a correction note.
- Evidence/share URLs include a stable segment reference and resolve through the current
  publication mapping where safe.
- Withdrawal tombstones all search documents immediately and blocks new media URLs.

## 14. Search architecture

### Search unit

Index evidence-sized passages, not whole transcripts. A search document may group a
small number of adjacent same-context evidence segments, but always returns the original
segment IDs and exact start/end time.

Index kinds:

- reviewed evidence passage;
- topic summary plus aliases and evidence;
- contribution summary plus evidence;
- bounded chunk of a visible official document.

Generated text expands recall; evidence remains the destination.

### Phase A: lexical and structured search

Use existing PostgreSQL first:

- exact phrase and normalized substring matching;
- weighted PostgreSQL full-text search using the `simple` configuration initially;
- `pg_trgm` for names, spelling errors, and near matches;
- filters for organization, body, date range, speaker, agenda, language, and document
  type;
- boosts for exact phrase, topic/agenda title, confirmed speaker, and reviewed evidence;
- no opaque recency or political-importance boost.

Keep original display text. Build a separately normalized text field for case/diacritic
handling and stable index expressions. Evaluate Galician behavior rather than applying a
Spanish stemmer by assumption.

### Phase B: semantic candidates

After the lexical evaluation baseline:

- enable PlanetScale `pgvector` through the supported operational path;
- use the same PostgreSQL major locally with an image that includes the extension;
- pin one multilingual embedding model and dimension;
- embed batched search documents through Workers AI;
- store embedding model/version/content hash;
- exact vector scan is acceptable for the small pilot corpus;
- introduce HNSW only after `EXPLAIN ANALYZE` and corpus benchmarks justify it.

Do not adopt Vectorize or AI Search merely because they exist. Benchmark them only if
PostgreSQL scale, latency, operational isolation, or managed hybrid features provide a
measured advantage.

### Hybrid ranking

1. Parse explicit filters and query intent.
2. Fetch lexical candidates.
3. Fetch semantic candidates when the index/model is ready.
4. Fuse ranks using Reciprocal Rank Fusion.
5. Apply transparent deterministic boosts.
6. Optionally rerank a bounded top set only after evaluation shows value.
7. Group adjacent duplicate evidence.
8. Return explanation flags: exact phrase, related meaning, speaker, agenda, document.

Natural-language “when” queries return chronological occurrences. “Who” queries group
confirmed/labelled speakers. Neither requires a chatbot answer.

### Public result contract

Every result includes:

- public session title, organization/body, and date;
- evidence excerpt;
- identified or explicitly unidentified speaker;
- agenda/topic context;
- start/end milliseconds and playback URL parameters;
- matched-field/reason flags;
- related visible documents;
- publication and evidence IDs;
- no private provider/model score.

### Assisted answers — later only

Do not implement until hybrid retrieval passes evaluation. If implemented:

- retrieve only active public publication data;
- cite every sentence to evidence/documents;
- say that evidence was not found rather than fill gaps;
- expose the supporting result cards before/alongside prose;
- rate-limit and budget;
- defend against prompt injection;
- evaluate political neutrality and no-answer behavior.

## 15. Public frontend experience

### Routes

Keep current routes working, then converge toward:

- `/projects` — authenticated intention/home and recent work;
- `/upload?mode=institution` — common upload wizard;
- `/editor/:id` — single editor/review/publication workspace;
- `/institution/portal` — organization sessions and publication management;
- `/transparencia` — public global archive/search;
- `/transparencia/:organization/sessions/:slug` — stable public session;
- timestamp state through `?t=<seconds>` and evidence fragment/parameter.

Do not create separate transcript, agenda-review, guide-review, and publication products.

### Upload wizard

Step 1 — media:

- file or supported source URL;
- format/size/duration validation before upload;
- clear provider size constraint;
- resumable/retry language even if the first PUT implementation restarts the upload;
- leave-page warning only during active upload.

Step 2 — context:

- title, language, organization/body/date/type/location;
- original public source URL;
- optional agenda paste/import;
- optional official documents;
- permission/retention acknowledgement.

Step 3 — confirm:

- what will be uploaded and processed;
- estimated usage range when measurable;
- automatic-transcription notice;
- “Upload and process” action.

After confirmation, open the editor immediately with durable background status.

### Editor information architecture

Top bar:

- title, lifecycle state, save state;
- exact primary action for current state;
- retry/help on failure.

Center:

- durable video player;
- transcript and timeline synchronized;
- timestamp copy/share.

Side rail:

- Transcript;
- Review issues with count;
- Session/agenda;
- Documents;
- Publication.

The Review tab disappears or becomes a resolved summary after the required gate. The
Session tab shows automation results and only optional quick checks.

### Public archive/search

- one prominent search box accepting terms or questions;
- filters hidden behind simple chips/drawer until used;
- evidence result cards, not AI chat bubbles;
- empty state suggests broader terms and allows clearing filters;
- “No evidence found” distinct from technical failure;
- query preserved in URL for sharing/back navigation;
- keyboard navigation and screen-reader result counts;
- mobile cards retain excerpt, speaker, date, and play-from-time action.

### Public session page

Above the fold:

- title, body, date, duration, publication/correction status;
- source link;
- video player;
- “Search this session.”

Below:

- agenda/topic index;
- synchronized transcript with speaker labels;
- guide clearly marked as assisted and evidence-linked;
- official documents with provenance;
- downloads and stable sharing.

Selecting a search result:

- loads/seeks the video;
- focuses/highlights the evidence passage;
- updates the timestamp URL without losing search context;
- never starts unexpected autoplay with sound.

### UX language and accessibility

- Galician is the primary interface language; search must be tested with Galician and
  Spanish queries.
- Use plain status language, not infrastructure/provider names.
- No confidence percentages; explain signals.
- Unknown speakers remain unknown.
- All review actions work by keyboard.
- Focus follows issue/result navigation.
- Player, transcript, highlights, dialogs, progress, and error states meet WCAG-oriented
  keyboard/contrast/announcement expectations.
- Respect reduced motion.

## 16. API surface

Exact request/response schemas must be documented and contract-tested before frontend
implementation. Proposed resources:

### Authenticated project APIs

- `POST /projects` — create metadata-first project; retain legacy upload adapter during
  migration.
- `GET /projects/{id}/processing`
- `GET /projects/{id}/transcript`
- `PATCH /projects/{id}/segments/{segmentId}`
- `PATCH /projects/{id}/speakers/{speakerId}`
- `GET /projects/{id}/review-issues`
- `POST /projects/{id}/review-issues/{issueId}/resolve`
- `POST /projects/{id}/review/complete`
- agenda CRUD/import/alignment endpoints;
- guide preview/exception/decision endpoints;
- document metadata/visibility endpoints;
- publication prepare/publish/correct/withdraw endpoints.

### Processing gateway

- upload intent create/complete/abort;
- processing job status/retry;
- provider webhook;
- signed playback/download URL issuance.

### Internal signed processing APIs

- job state transitions;
- provider request correlation;
- ingest normalized transcript;
- submit validated agenda/guide output;
- submit embeddings/index completion;
- safe failure/usage reporting.

All transitions are conditional on expected project/job/revision version. Duplicate and
out-of-order calls are idempotent or rejected without corrupting state.

### Public APIs

- public organization/session archive;
- public session by organization/slug;
- within-session/global search;
- public evidence context;
- short-lived media/document access where needed;
- search click/feedback event with privacy controls.

Public DTOs are explicit allowlists. Never serialize JPA entities or private R2 keys.

## 17. Security, privacy, and integrity

- Private R2 bucket; short-lived signed URLs are bearer credentials.
- Validate upload size, content type, checksum/eTag, project ownership, and intent expiry.
- Only the system generates provider `source_url`; do not let arbitrary user URLs become
  server-side fetch targets without a separate SSRF-safe import design.
- Verify ElevenLabs webhook HMAC against the raw request body before parsing.
- Sign internal Worker→Spring commands and reject replay.
- Strip cookies/authorization from provider calls and logs.
- Rate-limit upload intents, retries, public search, and future assisted answers.
- Enforce organization/project ownership on every authenticated child resource.
- Index only active public publication snapshots.
- Treat transcript/document text as untrusted prompt data.
- Store model/prompt/schema versions and content hashes for reproducibility.
- Establish retention/deletion for originals, provider payloads, generated artifacts,
  withdrawn publications, job logs, and search analytics.
- Search analytics must not profile political ideology. Minimize IP/user-agent retention
  and document the lawful purpose before production.
- Add governance work for DPA, subprocessors, data location, ENS/RGPD gap, accessibility,
  correction, export, and contract exit before processing non-public institutional data.

## 18. Phased vertical implementation

Only one phase should be “in progress.” Update this table after every completed slice.

| Phase | Status | Deployable outcome |
|---|---|---|
| 0 — baseline and contracts | Complete (2026-07-29) | real Scribe v2 fixture/metrics, schemas, flags, evaluation seeds |
| 1 — normalized evidence model | Complete (2026-07-29) | legacy-safe revisions/speakers/segments/jobs in PostgreSQL |
| 2 — R2 and async ingestion | Implemented locally; hosted smoke pending | durable upload, Workflow/webhook transcription, processing UI |
| 3 — exception-only transcript review | Implemented locally; task study pending | required issue queue, speaker/text corrections, review metrics |
| 4 — automatic agenda and guide | Implemented locally; 2–5 min field gate pending | alignment, structured extraction, validation, quick checks |
| 5 — publication and public session | Implemented and boundary-tested locally | immutable snapshot, stable public page, playback/documents |
| 6 — lexical public search | Implemented locally; labelled-corpus gate pending | exact/FTS/trigram search, filters, within-session/global UX |
| 7 — hybrid search | Implemented dark; relevance/cost gate pending | embeddings, pgvector candidates, RRF, measured rollout |
| 8 — pilot hardening | In progress | real hosted ingest/webhook/Range and review UI smoke verified 10 September; downstream activation, hosted load, human task studies, accessibility and governance gates pending |

### Phase 0 — baseline and contracts

API repository:

- run one real hosted-dev Scribe v2 transcription on a public Galician fixture;
- capture a sanitized provider-contract fixture including diarization and useful signals;
- record duration, latency, provider usage/cost, payload size, and failure behavior;
- define JSON Schemas for normalized transcript and guide output;
- define state/error code enums and transition tests;
- add search-evaluation JSONL schema and initial representative queries;
- add decision-log entries from this plan.

Frontend:

- write task-level UX acceptance scenarios for upload, issue review, quick checks,
  publication, search, and evidence playback;
- define status/error copy in Galician;
- identify accessibility expectations.

Gate:

- existing tests/builds green;
- real provider behavior documented;
- no secret or private media in fixtures;
- schema/API review complete before migrations.

### Phase 1 — normalized evidence model

API:

- Flyway migrations for organization-ready project fields, recordings, revisions,
  speakers, segments, review issues, edit events, processing jobs/events;
- repositories/services/DTOs with ownership and optimistic locking;
- adapter that reads legacy `Project.words`;
- endpoint returning normalized transcript for new projects;
- unit/integration/migration tests with PostgreSQL 17 Testcontainers.

Frontend:

- update project types/state adapter to accept legacy or normalized transcript;
- keep existing editor behavior unchanged visually;
- add lifecycle status component behind a server capability flag.

Gate:

- no forced rewrite of legacy rows;
- creator workflow tests unchanged;
- cross-user access tests for every new resource;
- migration rollback documented as forward-fix/feature-disable, not destructive reset.

### Phase 2 — R2 and asynchronous ingestion

Processing Worker:

- scaffold inside API repo with current Workers guidance;
- configure dev R2/AI/Workflow/API bindings and generated types;
- implement signed upload intent and completion verification;
- implement ingest Workflow, ElevenLabs source URL submission, webhook HMAC, R2 raw
  artifact, event resume, normalization, and signed internal API client;
- structured logging, retries, idempotency, tests, dry-run deployment.

Spring:

- canonical upload/job transition endpoints;
- provider/internal signature verification;
- remove `file.getBytes()` from the new institutional path;
- keep legacy creator upload until the unified adapter is ready.

Frontend:

- direct upload with real progress and retry;
- processing state in editor;
- local fallback/test strategy;
- leave/reopen flow proving processing is durable.

Operations:

- dev bucket/Worker/Workflow/webhook/bindings/secrets;
- CI checks: typecheck, Workers tests, `wrangler deploy --dry-run`;
- update launch/secrets/architecture runbooks.

Gate:

- upload survives browser close;
- duplicate completion/webhook is harmless;
- provider/R2 failure yields retryable state;
- no large payload crosses Workflow persistence;
- public requests cannot invoke internal writes.

### Phase 3 — exception-only transcript review

API:

- issue generation rules from ASR signals;
- edit/assign/merge/resolve transactions;
- completion gate and timing metrics;
- fork/freeze groundwork.

Frontend:

- exception queue, video jump, context, smallest-action controls;
- complete transcript remains available but is not the required path;
- finish-review action and resolved summary;
- responsive/keyboard/screen-reader behavior.

Gate:

- fixture task can be completed without reading the whole transcript;
- unknown speakers stay unknown;
- no fake accuracy score;
- instrumentation reports active review time and issue outcomes.

### Phase 4 — automatic agenda and structured guide

API:

- agenda, alignment, guide, topic, contribution, decision, evidence-link tables/services;
- review-complete event creates enrichment job;
- quick-check and decision-confirmation transitions.

Processing Worker:

- deterministic anchor/alignment implementation;
- bounded schema-constrained Workers AI extraction;
- prompt-injection-resistant prompt/data separation;
- deterministic evidence validator;
- content-hash incremental regeneration;
- raw generated artifacts in private R2.

Frontend:

- agenda import/paste;
- automatic agenda visualization;
- optional quick-check queue;
- guide preview with evidence links;
- consequential decisions confirm/edit/omit;
- no second exhaustive approval workflow.

Gate:

- every displayed guide object has valid evidence;
- unsafe decision omitted by default;
- missing agenda still produces searchable transcript and optional topic guide;
- added enrichment human work meets the 2–5 minute target on normal samples or the
  phase does not graduate.

### Phase 5 — publication and public session

API:

- publication snapshot/freeze/fork/withdraw;
- slug and public allowlist DTOs;
- visible-document provenance;
- playback/document access authorization;
- correction/version behavior.

Frontend:

- publication checklist distinguishing required/enhanced;
- preview;
- stable public session page with video, transcript, agenda/guide, documents, evidence
  links, timestamp sharing;
- correction/withdrawn states.

Gate:

- anonymous public access works;
- private/draft material never leaks;
- published evidence is immutable;
- Range playback and timestamp navigation work;
- lexical-index failure cannot corrupt publication.

### Phase 6 — lexical search

API:

- extensions/normalized search fields/search documents and indexes;
- publication indexing/tombstoning;
- exact phrase, FTS, trigram, filters, transparent boosts;
- within-session/global endpoints;
- privacy-minimized query/click metrics.

Frontend:

- replace demo `.includes` explorer with API-backed URL-preserving search;
- evidence cards, filters, empty/error states;
- session search and jump-to-time;
- keyboard/mobile/accessibility validation.

Evaluation:

- label a gold set across exact quote, paraphrase, name, typo, date, agenda, “who,”
  “when,” bilingual, and no-answer queries;
- record Recall@20, MRR/nDCG, zero-result rate, task success, and time-to-evidence.

Gate:

- lexical baseline meets agreed task-success threshold;
- every result resolves to active evidence;
- query plans and p95 latency acceptable at pilot corpus size.

### Phase 7 — hybrid search

Operations/API:

- enable pgvector deliberately in local/dev;
- add model-dimension-compatible column/migration;
- embed active search documents via index Workflow;
- semantic candidate query and RRF;
- version/reindex/tombstone behavior.

Frontend:

- no chat redesign;
- optionally disclose “related meaning” match reason;
- graceful lexical-only behavior while semantic index is unavailable.

Evaluation:

- compare lexical, semantic, and hybrid on the same labelled queries;
- adopt hybrid only if it materially improves task success/recall without unacceptable
  latency, cost, or misleading matches;
- benchmark AI Search/Vectorize only as documented alternatives, not dependencies.

Gate:

- model tested on real Galician content;
- no-answer precision and political neutrality reviewed;
- budget and reindex cost measured.

### Phase 8 — pilot hardening

- cross-repository E2E in local and hosted dev;
- cold-start, long-session, concurrent-search, Range playback, and retry tests;
- R2/database restore and publication reindex rehearsal;
- dependency/security review, secret rotation, webhook replay test;
- WCAG-oriented audit and real task studies;
- cost alerts and per-session margin report;
- DPA/subprocessor/data-location/retention/ENS-RGPD/accessibility package;
- production annex only after pilot/date/domain approval.

## 19. Verification matrix

### API

- Java unit tests for state transitions, normalization, validators, ranking;
- Spring integration tests with Testcontainers;
- ownership/CSRF/internal-HMAC/public-allowlist tests;
- Flyway migration test from current schema and empty schema;
- Scribe fixture contract test;
- publication immutability/correction/tombstone tests;
- SQL `EXPLAIN ANALYZE` fixtures for search indexes.

### Processing Worker

- TypeScript strict typecheck and generated binding types;
- Workers Vitest pool tests;
- Workflow replay/idempotency and deterministic-step tests;
- webhook raw-body HMAC/replay tests;
- R2 missing/duplicate/checksum/expiry tests;
- provider retry/non-retryable classification;
- schema and prompt-injection adversarial fixtures;
- dry-run deploy and hosted-dev smoke.

### Frontend

- component/API tests for every state and feature capability;
- upload progress/retry/reopen tests;
- review queue navigation and optimistic-conflict tests;
- publication checklist and public DTO tests;
- search URL/filter/result/empty/error tests;
- player seek/timestamp/evidence focus tests;
- accessibility assertions;
- browser E2E for the critical institutional and public journeys.

### Required repository commands before phase handoff

Frontend:

```powershell
npm test
npm run build
npm run deploy:dev:dry-run
```

API:

```powershell
.\mvnw.cmd -B test
npm run cf-typegen
npm run typecheck:worker
npm run test:processing-worker
npm run test:search-evaluation
npm run deploy:dev:dry-run
```

The API-root scripts and CI execute the processing Worker test, typecheck, generated-type,
evaluation, and development dry-run checks from the same repository.

## 20. Rollout, feature control, and rollback

Capabilities are returned by the authenticated/public API; the frontend must not assume
that every environment/project has every phase enabled.

Initial capabilities:

- durable institutional upload;
- normalized transcript;
- exception review;
- automatic agenda;
- structured guide;
- public publication;
- lexical search;
- hybrid search.

Rollout sequence:

1. schema dark;
2. write new model while reading legacy;
3. internal staff project;
4. hosted-dev public sample;
5. pilot organization;
6. broader development;
7. production only through launch annex.

Rollback:

- disable capability and fall back to last stable vertical slice;
- preserve uploaded media, raw provider artifact, transcript revision, and job state;
- publication remains lexical if embeddings/index Worker fail;
- enrichment may be omitted without losing transcript publication;
- do not drop columns/tables in an emergency rollback;
- use forward fixes and tombstones;
- never reset the database or delete R2 prefixes to roll back application code.

## 21. Plan changelog

| Date | Change | Reason |
|---|---|---|
| 2026-09-10 | Resumed authorized development rollout; real hosted ingest and browser review smoke; Windows Docker recovery; authenticated dev lifecycle controls; processor language/name-warning fixes | Current evidence in `../operations/capability-rollout-2026-09-10.md`. No phase graduated from synthetic smoke; production remains gated. |
| 2026-09-08 | Recovered September Windows commits and unversioned flow diagrams; consolidated continuity and capability/credential inventory | No phase graduated or hosted capability enabled. September sessions report PlanetScale extensions and R2 account activation complete, with local E2E and hosted provisioning still pending. Separate Omarchy navigation/coherence work explicitly deferred by the user; see `../operations/continuity-2026-09-08.md`. |
| 2026-07-29 | Initial canonical plan | Consolidates durable ingestion, evidence model, minimal human review, structured guide, public publication, and hybrid search discussion |
| 2026-07-29 | Phase 0 completed; Phase 1 started | Captured a sanitized real Scribe v2 contract and metrics, fixed current provider assumptions, versioned schemas/API/UX contracts, added fail-closed flags and evaluation seeds, and passed 67 API tests plus 175 frontend tests/build |
| 2026-07-29 | Phase 1 completed; Phase 2 started | Added additive PostgreSQL evidence/job migrations, organization-ready project lifecycle, optimistic entities, legacy transcript adapter, owner-scoped transcript/processing APIs, capability-gated lifecycle UX, forward-fix rollback policy, and passed 71 API plus 179 frontend tests/build |
| 2026-07-30 | Phases 2–3 implemented locally | Added private-R2 upload intents, signed internal boundary, Workflows/Scribe webhook ingestion and durable retry; added exception-only review, edits, freeze gate and active-time metrics without a second exhaustive pass |
| 2026-07-30 | Phases 4–5 implemented locally | Added monotonic agenda alignment, schema-constrained evidence validation, assisted guide/decision exceptions, immutable publication/correction/withdrawal snapshots, public allowlist DTOs, Range media and reload-safe operator UI |
| 2026-07-30 | Phase 6 implemented locally | Added exact phrase, PostgreSQL FTS, keyword and trigram candidates, filters/boosts, public/session APIs, privacy-HMAC analytics, evidence-first frontend search and a reusable labelled evaluation runner |
| 2026-07-30 | Phase 7 implemented dark | Added pgvector 1024 projection, BGE-M3 batch Workflow, exact semantic candidates plus RRF, lexical fallback, semantic-pending UX and versioned reindex; capability remains false until Galician/bilingual relevance and no-answer gates pass |
| 2026-07-30 | Phase 8 hardening started | Added edge/API rate limits, retry controls, scheduled abandoned-upload and 90-day analytics cleanup, in-place restore reindex, configured provider/AI cost capture, owner pilot metrics and a deployment/restore/rollback runbook; no hosted resources or secrets changed |
| 2026-07-30 | Local implementation verified | Passed 78 Spring/PostgreSQL-pgvector tests, 23 processing Worker tests, 186 frontend tests, strict typechecks, Next.js/OpenNext production build, container image build and all Cloudflare dry-runs; hosted, gold-set, load, accessibility and governance gates remain open and all post-normalization capabilities remain dark |

## 22. Handoff template

Every session completing work on this plan should leave:

```text
Current phase:
Completed slice:
Files/migrations/resources changed:
Feature capabilities enabled:
Verification run and results:
Hosted resources changed:
Secrets/bindings added or rotated (names only):
Metrics/observations:
Known risks or failures:
Exact next safe step:
Plan/decision/runbook documents updated:
```

If a phase gate is not satisfied, say so plainly and leave the capability disabled.
