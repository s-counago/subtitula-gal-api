-- Phase 1 is deliberately additive. Existing creator/institution projects keep their
-- projects.words payload and are exposed through an adapter until a future explicit
-- migration writes normalized revisions.

create table organizations (
    id               uuid primary key,
    name             varchar(255) not null,
    slug             varchar(120) not null unique,
    branding         jsonb,
    retention_policy jsonb,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    version          bigint not null default 0,
    constraint chk_organizations_slug
        check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

create table organization_members (
    id            uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    user_id       uuid not null references users(id) on delete cascade,
    role          varchar(20) not null,
    member_state  varchar(20) not null default 'ACTIVE',
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    version       bigint not null default 0,
    constraint uq_organization_members unique (organization_id, user_id),
    constraint chk_organization_member_role
        check (role in ('OWNER', 'PUBLISHER', 'REVIEWER')),
    constraint chk_organization_member_state
        check (member_state in ('INVITED', 'ACTIVE', 'DISABLED'))
);
create index idx_organization_members_user on organization_members(user_id);

alter table projects
    add column organization_id uuid references organizations(id) on delete set null,
    add column status varchar(40) not null default 'READY',
    add column session_date date,
    add column session_body varchar(255),
    add column location varchar(255),
    add column session_type varchar(40),
    add column failure_code varchar(80),
    add column failure_message varchar(500),
    add column archived_at timestamptz,
    add column version bigint not null default 0;

alter table projects
    add constraint chk_projects_status check (status in (
        'DRAFT', 'UPLOADING', 'UPLOADED', 'TRANSCRIBING', 'REVIEW_REQUIRED',
        'ENRICHING', 'READY', 'PUBLISHED', 'ARCHIVED', 'PROCESSING_FAILED'
    ));
create index idx_projects_organization on projects(organization_id);
create index idx_projects_status on projects(status);

create table recordings (
    id                    uuid primary key,
    project_id            uuid not null references projects(id) on delete cascade,
    object_key            varchar(1024) not null unique,
    original_source_url   text,
    original_filename     varchar(500),
    mime_type             varchar(255) not null,
    size_bytes            bigint not null,
    duration_ms           bigint,
    checksum_sha256       varchar(64),
    etag                  varchar(255),
    upload_state          varchar(30) not null,
    usage_permission      boolean not null default false,
    visibility            varchar(20) not null default 'PRIVATE',
    provider_source_state varchar(30) not null default 'NOT_ISSUED',
    verified_at           timestamptz,
    deleted_at            timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    version               bigint not null default 0,
    constraint chk_recordings_size check (size_bytes >= 0),
    constraint chk_recordings_duration check (duration_ms is null or duration_ms >= 0),
    constraint chk_recordings_checksum
        check (checksum_sha256 is null or checksum_sha256 ~ '^[a-f0-9]{64}$'),
    constraint chk_recordings_upload_state
        check (upload_state in ('INTENT_CREATED', 'UPLOADING', 'UPLOADED', 'VERIFIED', 'ABORTED', 'EXPIRED', 'DELETED')),
    constraint chk_recordings_visibility
        check (visibility in ('PRIVATE', 'PUBLIC')),
    constraint chk_recordings_provider_source_state
        check (provider_source_state in ('NOT_ISSUED', 'ACTIVE', 'EXPIRED', 'REVOKED'))
);
create index idx_recordings_project on recordings(project_id, created_at desc);

create table transcript_revisions (
    id                   uuid primary key,
    project_id           uuid not null references projects(id) on delete cascade,
    version_number       integer not null,
    parent_revision_id   uuid references transcript_revisions(id) on delete set null,
    source               varchar(30) not null,
    provider             varchar(80),
    model                varchar(120),
    language_code        varchar(20),
    prompt_version       varchar(80),
    keyterm_version      varchar(80),
    state                varchar(30) not null,
    raw_artifact_key     varchar(1024),
    content_hash         varchar(64),
    created_by           uuid references users(id) on delete set null,
    created_at           timestamptz not null default now(),
    frozen_by            uuid references users(id) on delete set null,
    frozen_at            timestamptz,
    version              bigint not null default 0,
    constraint uq_transcript_revision_number unique (project_id, version_number),
    constraint chk_transcript_revision_number check (version_number > 0),
    constraint chk_transcript_revision_source
        check (source in ('ASR', 'HUMAN_EDIT', 'CORRECTION', 'LEGACY')),
    constraint chk_transcript_revision_state
        check (state in ('WORKING', 'FROZEN', 'SUPERSEDED')),
    constraint chk_transcript_revision_hash
        check (content_hash is null or content_hash ~ '^[a-f0-9]{64}$')
);
create index idx_transcript_revisions_project
    on transcript_revisions(project_id, version_number desc);

create table speakers (
    id               uuid primary key,
    project_id       uuid not null references projects(id) on delete cascade,
    provider_label   varchar(120),
    display_label    varchar(255) not null,
    confirmed_name   varchar(255),
    speaker_role     varchar(255),
    identity_state   varchar(30) not null,
    source           varchar(30) not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    version          bigint not null default 0,
    constraint chk_speakers_identity_state
        check (identity_state in ('UNKNOWN', 'LABELLED', 'CONFIRMED')),
    constraint chk_speakers_source
        check (source in ('ASR', 'HUMAN', 'LEGACY'))
);
create unique index uq_speakers_project_provider_label
    on speakers(project_id, provider_label)
    where provider_label is not null;
create index idx_speakers_project on speakers(project_id);

create table evidence_segments (
    id                     uuid primary key,
    transcript_revision_id uuid not null references transcript_revisions(id) on delete cascade,
    sequence               integer not null,
    start_ms               bigint not null,
    end_ms                 bigint not null,
    speaker_id             uuid references speakers(id) on delete set null,
    original_text          text not null,
    reviewed_text          text not null,
    word_timings           jsonb not null default '[]'::jsonb,
    review_state           varchar(30) not null default 'UNREVIEWED',
    signals                jsonb not null default '{}'::jsonb,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint not null default 0,
    constraint uq_evidence_segment_sequence unique (transcript_revision_id, sequence),
    constraint chk_evidence_segment_sequence check (sequence >= 0),
    constraint chk_evidence_segment_times check (start_ms >= 0 and end_ms >= start_ms),
    constraint chk_evidence_segment_original check (length(btrim(original_text)) > 0),
    constraint chk_evidence_segment_reviewed check (length(btrim(reviewed_text)) > 0),
    constraint chk_evidence_segment_review_state
        check (review_state in ('UNREVIEWED', 'REVIEWED', 'NEEDS_ATTENTION'))
);
create index idx_evidence_segments_revision_time
    on evidence_segments(transcript_revision_id, sequence);
create index idx_evidence_segments_speaker on evidence_segments(speaker_id);

create table transcript_edit_events (
    id                     uuid primary key,
    transcript_revision_id uuid not null references transcript_revisions(id) on delete cascade,
    segment_id             uuid references evidence_segments(id) on delete set null,
    actor_id               uuid references users(id) on delete set null,
    edit_kind              varchar(40) not null,
    prior_content_hash     varchar(64),
    new_content_hash       varchar(64) not null,
    created_at             timestamptz not null default now(),
    constraint chk_transcript_edit_prior_hash
        check (prior_content_hash is null or prior_content_hash ~ '^[a-f0-9]{64}$'),
    constraint chk_transcript_edit_new_hash
        check (new_content_hash ~ '^[a-f0-9]{64}$'),
    constraint chk_transcript_edit_kind
        check (edit_kind in ('TEXT', 'SPEAKER', 'MERGE', 'SPLIT', 'TIMING', 'REVISION_CREATED'))
);
create index idx_transcript_edit_events_revision
    on transcript_edit_events(transcript_revision_id, created_at);

create table review_issues (
    id                     uuid primary key,
    project_id             uuid not null references projects(id) on delete cascade,
    transcript_revision_id uuid not null references transcript_revisions(id) on delete cascade,
    segment_id             uuid references evidence_segments(id) on delete cascade,
    speaker_id             uuid references speakers(id) on delete set null,
    issue_type             varchar(50) not null,
    severity               varchar(20) not null,
    signals                jsonb not null default '{}'::jsonb,
    state                  varchar(20) not null default 'OPEN',
    resolution             varchar(80),
    resolved_by            uuid references users(id) on delete set null,
    resolved_at            timestamptz,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint not null default 0,
    constraint chk_review_issue_type check (issue_type in (
        'UNKNOWN_SPEAKER', 'SPEAKER_CHANGE', 'LOW_CONFIDENCE_SPAN',
        'PROBABLE_PROPER_NAME', 'GLOSSARY_MISMATCH', 'OVERLAP_OR_NOISE',
        'MISSING_TIMING', 'INVALID_SEGMENT'
    )),
    constraint chk_review_issue_severity check (severity in ('REQUIRED', 'WARNING')),
    constraint chk_review_issue_state check (state in ('OPEN', 'RESOLVED', 'DISMISSED'))
);
create index idx_review_issues_project_state
    on review_issues(project_id, state, severity);
create index idx_review_issues_revision on review_issues(transcript_revision_id);

create table processing_jobs (
    id                    uuid primary key,
    project_id            uuid not null references projects(id) on delete cascade,
    job_type              varchar(20) not null,
    workflow_instance_id  varchar(255),
    provider_request_id   varchar(255),
    state                 varchar(30) not null,
    current_stage         varchar(50) not null,
    attempt_count         integer not null default 0,
    idempotency_key       varchar(255) not null unique,
    input_hash            varchar(64),
    output_hash           varchar(64),
    input_artifact_key    varchar(1024),
    output_artifact_key   varchar(1024),
    model_version         varchar(120),
    prompt_version        varchar(120),
    index_version         varchar(120),
    safe_error_code       varchar(80),
    safe_error_message    varchar(500),
    provider_usage        jsonb not null default '{}'::jsonb,
    cost_microunits       bigint,
    cost_currency         varchar(3),
    started_at            timestamptz,
    completed_at          timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    version               bigint not null default 0,
    constraint chk_processing_job_type
        check (job_type in ('INGEST', 'ENRICH', 'INDEX', 'REINDEX', 'DELETE')),
    constraint chk_processing_job_state check (state in (
        'QUEUED', 'RUNNING', 'WAITING', 'SUCCEEDED',
        'FAILED_RETRYABLE', 'FAILED_TERMINAL', 'CANCELLED'
    )),
    constraint chk_processing_job_attempt check (attempt_count >= 0),
    constraint chk_processing_job_input_hash
        check (input_hash is null or input_hash ~ '^[a-f0-9]{64}$'),
    constraint chk_processing_job_output_hash
        check (output_hash is null or output_hash ~ '^[a-f0-9]{64}$'),
    constraint chk_processing_job_cost check (cost_microunits is null or cost_microunits >= 0)
);
create unique index uq_processing_jobs_workflow
    on processing_jobs(workflow_instance_id)
    where workflow_instance_id is not null;
create unique index uq_processing_jobs_provider_request
    on processing_jobs(provider_request_id)
    where provider_request_id is not null;
create index idx_processing_jobs_project_created
    on processing_jobs(project_id, created_at desc);
create index idx_processing_jobs_active
    on processing_jobs(state, current_stage)
    where state in ('QUEUED', 'RUNNING', 'WAITING', 'FAILED_RETRYABLE');

create table processing_events (
    id             uuid primary key,
    job_id         uuid not null references processing_jobs(id) on delete cascade,
    stage          varchar(50) not null,
    status         varchar(30) not null,
    duration_ms    bigint,
    provider_usage jsonb not null default '{}'::jsonb,
    correlation_id varchar(255) not null,
    created_at     timestamptz not null default now(),
    constraint chk_processing_event_duration
        check (duration_ms is null or duration_ms >= 0),
    constraint chk_processing_event_status
        check (status in ('STARTED', 'SUCCEEDED', 'FAILED', 'WAITING', 'RETRIED'))
);
create index idx_processing_events_job
    on processing_events(job_id, created_at);
