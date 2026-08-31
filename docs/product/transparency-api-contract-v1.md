# Transparency evidence API contract v1

**Status:** implemented and contract-tested locally; rollout capabilities remain
server-controlled and mostly disabled.

All IDs are UUIDs. Timestamps are ISO-8601 UTC; playback offsets are integer
milliseconds. Authenticated mutations require the existing session cookie and
CSRF echo. Internal routes require HMAC authentication. Public responses are
explicit allowlists and never expose R2 keys, provider request IDs, prompts,
embeddings or ranking scores.

## Errors and concurrency

HTTP errors use a stable machine code:

```json
{
  "error": "transparency_conflict",
  "message": "Safe bounded message"
}
```

Mutable aggregates carry an optimistic `version`. Commands that can race include
the expected version; stale or out-of-order commands return 409 without partial
writes. Workflow retries use stable job IDs and command-specific idempotency.

## Capabilities

`GET /capabilities` is anonymous:

```json
{
  "contractVersion": "1.0.0",
  "durableInstitutionalUpload": false,
  "normalizedTranscript": true,
  "exceptionReview": false,
  "automaticAgenda": false,
  "structuredGuide": false,
  "publicPublication": false,
  "lexicalSearch": false,
  "hybridSearch": false
}
```

Absent fields are false. The frontend does not infer a capability from the
presence of data.

## Authenticated Spring resources

### Project and processing

- `POST /projects` with institutional JSON creates a metadata-first draft; the
  multipart creator route remains as a legacy adapter.
- `GET /projects/{projectId}/processing` returns project lifecycle plus the most
  recent bounded job.
- `GET /projects/{projectId}/pilot-metrics` returns owner-only active review
  time, publication time, configured gross provider/AI costs, per-media-hour
  cost and attributed public-search aggregates.

The processing job includes `type`, `state`, `stage`, `attempt`,
`lastUpdatedAt`, bounded error fields, `retryable`, `costMicrounits`,
`costCurrency`, `startedAt`, `completedAt` and `version`. Provider usage is not
returned to the browser.

### Transcript and exception review

- `GET /projects/{projectId}/transcript`
- `GET /projects/{projectId}/review-issues`
- `POST /projects/{projectId}/review/open`
- `POST /projects/{projectId}/review/activity`
- `PATCH /projects/{projectId}/segments/{segmentId}`
- `PATCH /projects/{projectId}/speakers/{speakerId}`
- `POST /projects/{projectId}/review-issues/{issueId}/resolve`
- `POST /projects/{projectId}/review/complete`

The transcript contains one revision, ordered speakers and timestamped evidence
segments. Legacy projects return the same read shape with `legacyAdapter=true`.
Only required issues block freeze. Review activity is gap-bounded and is not a
wall-clock timer.

### Agenda and assisted guide

- `GET|PUT /projects/{projectId}/agenda`
- `PATCH /projects/{projectId}/agenda-alignments/{alignmentId}`
- `GET /projects/{projectId}/guide`
- `PATCH /projects/{projectId}/guide/decisions/{decisionId}`

An agenda replacement includes project version. Alignment changes are optional
exception checks. Every returned topic, contribution and visible decision has
resolvable evidence. Candidate decisions are omitted from the public guide
until confirmed/document-supported.

### Documents and publication

- `GET|POST /projects/{projectId}/documents`
- `GET /projects/{projectId}/publication-checklist`
- `GET /projects/{projectId}/publications/latest`
- `POST /projects/{projectId}/publications`
- `POST /projects/{projectId}/corrections`
- `POST /projects/{projectId}/publications/{publicationId}/withdraw`
- `POST /projects/{projectId}/publications/{publicationId}/reindex`

Publication freezes/pins the recording, transcript revision, optional guide and
visible document IDs. A second version requires a non-empty public correction
note. Starting a correction forks a new working transcript and leaves the
current snapshot live. Withdrawal tombstones its search projection. Reindex is
owner-only, returns 202 plus a new job ID, preserves search-document identities
and clicks, and clears stale embeddings.

## Browser-facing Processing Worker

These routes are exposed through the frontend's same-origin `/processing/*`
gateway:

- `POST /processing/upload-intents`
- `PUT /processing/uploads/{intentId}` only for the local R2 adapter
- `POST /processing/upload-intents/{intentId}/complete`
- `POST /processing/upload-intents/{intentId}/abort`
- `POST /processing/projects/{projectId}/ingestion/retry`
- `POST /processing/projects/{projectId}/enrichment`
- `POST /processing/publications/{publicationId}/index`
- `GET /processing/projects/{projectId}/media-url`
- `GET|HEAD /processing/projects/{projectId}/media` only for the local adapter
- `GET /processing/search`
- `GET /processing/sessions/{slug}/search`
- `GET|HEAD /processing/publications/{slug}/media`
- `POST /webhooks/elevenlabs/speech-to-text`

Browser mutations require same-origin validation, CSRF echo, project ownership
and the mutation rate limiter before external work. Search is rate-limited
before Workers AI. Hosted upload intents return an S3 presigned PUT; local
returns a bounded Worker PUT. Both use:

```json
{
  "intentId": "uuid",
  "recordingId": "uuid",
  "jobId": "uuid",
  "uploadUrl": "short-lived bearer URL",
  "uploadMode": "presigned_put",
  "uploadToken": "short-lived opaque token",
  "method": "PUT",
  "allowedHeaders": {
    "content-type": "video/mp4"
  },
  "expiresAt": "2026-07-29T12:15:00Z",
  "sizeBytes": 123456
}
```

The frontend never stores or logs the URL/token. Private playback first uses
the local IndexedDB copy; otherwise `media-url` returns a renewable presigned
GET after owner authorization. Retry after verified upload starts a fresh
Workflow over the existing R2 object.

Disabled public capabilities return `404 capability_disabled` from Spring;
they are not merely hidden by the frontend. Signed cleanup/reconciliation
commands remain available so disabling a capability cannot strand existing
data or work.

Hybrid gateway behavior is fail-open to literal search: it embeds the query,
requests signed hybrid ranking, and on any AI/internal failure proxies the
corresponding `/public/.../search` response.

## Signed internal Spring resources

Required headers:

- `X-Subtitula-Timestamp`
- `X-Subtitula-Nonce`
- `X-Subtitula-Content-SHA256`
- `X-Subtitula-Signature`

The canonical string is uppercase method, **request path without query**,
timestamp seconds, nonce and SHA-256 of the exact body, separated by newlines.
Clock skew over five minutes, a repeated nonce, a mismatched digest or signature
is rejected before controller code.

Ingestion/enrichment:

- `POST /internal/processing/upload-intents`
- `POST /internal/processing/upload-intents/{intentId}/complete|abort`
- `GET /internal/processing/jobs/{jobId}/context`
- `POST /internal/processing/jobs/{jobId}/start`
- `POST /internal/processing/jobs/{jobId}/ingest-retry`
- `POST /internal/processing/jobs/{jobId}/provider-submitted`
- `POST /internal/processing/jobs/{jobId}/webhook-received`
- `POST /internal/processing/jobs/{jobId}/transcript`
- `GET /internal/processing/jobs/{jobId}/enrichment-context`
- `POST /internal/processing/jobs/{jobId}/enrichment-start`
- `POST /internal/processing/jobs/{jobId}/guide`
- `POST /internal/processing/jobs/{jobId}/agenda`
- `POST /internal/processing/jobs/{jobId}/failed`
- `POST /internal/processing/jobs/pending-workflows`

Search projection:

- `POST .../lexical-workflow`
- `POST .../lexical-index`
- `POST /internal/processing/jobs/{jobId}/lexical-index-failed`
- `GET /internal/processing/jobs/{jobId}/publications/{publicationId}/embedding-context`
- `POST .../embedding-start`
- `POST .../embeddings`
- `POST .../embedding-complete`
- `POST .../embedding-failed`
- `POST /internal/processing/search/hybrid`

Maintenance/media:

- `POST /internal/processing/cleanup/upload-intents/candidates`
- `POST /internal/processing/cleanup/upload-intents/{intentId}/complete`
- `POST /internal/processing/cleanup/recordings/candidates`
- `POST /internal/processing/cleanup/recordings/{recordingId}/complete`
- `POST /internal/processing/cleanup/search-analytics`
- `GET /internal/processing/projects/{projectId}/media`
- `GET /internal/processing/publications/{slug}/media`
- `GET /internal/processing/publications/{slug}/versions/{version}/media`

Large provider and generated artifacts remain in private R2. Transcript and
guide commands conform to the versioned JSON Schemas. Usage metadata must be a
bounded object; costs are non-negative microunits plus a three-letter currency.

## Public API

- `GET /public/sessions`
- `GET /public/sessions/{slug}?version={n}`
- `GET /public/search?q=...`
- `GET /public/sessions/{slug}/search?q=...`
- `POST /public/search/clicks`

Global search supports `organizationId`, `body`, `dateFrom`, `dateTo`,
`speakerId`, `agendaItemId`, `language`, `kind`, `limit` and `offset`.
Within-session search supports speaker, agenda, kind and pagination.

Search responses use:

```json
{
  "query": "orzamento da auga",
  "mode": "LEXICAL",
  "total": 1,
  "limit": 20,
  "offset": 0,
  "queryEventId": "uuid-or-null",
  "results": [
    {
      "searchDocumentId": "uuid",
      "publicationId": "uuid",
      "publicSlug": "pleno-xullo-2026",
      "publicationVersion": 1,
      "kind": "EVIDENCE",
      "sourceEntityId": "uuid",
      "evidenceSegmentId": "uuid",
      "sessionTitle": "Pleno de xullo",
      "excerpt": "…orzamento para as tubaxes de auga…",
      "speakerLabel": "Persoa non identificada",
      "startMs": 1902000,
      "endMs": 1928000,
      "evidenceHref": "/transparencia/pleno-xullo-2026?t=1902",
      "matchReasons": ["EXACT_PHRASE"]
    }
  ]
}
```

Hybrid uses the identical DTO and may add `RELATED_MEANING`; scores remain
private. Every result must join an active `PUBLISHED` snapshot. Empty evidence
is HTTP 200 with `results=[]`; a technical failure is non-2xx.

When analytics HMAC is blank, `queryEventId` is null and click persistence is a
no-op. When enabled, the API stores only keyed query digest, query length,
bounded filters, result count, latency, mode and configured cost estimate—never
raw query, IP or user-agent.
