create table upload_intents (
    id                    uuid primary key,
    project_id            uuid not null references projects(id) on delete cascade,
    recording_id          uuid not null unique references recordings(id) on delete cascade,
    processing_job_id     uuid not null unique references processing_jobs(id) on delete cascade,
    client_request_id     uuid not null,
    state                 varchar(20) not null,
    expires_at            timestamptz not null,
    completed_at          timestamptz,
    aborted_at            timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    version               bigint not null default 0,
    constraint uq_upload_intents_project_request unique (project_id, client_request_id),
    constraint chk_upload_intents_state
        check (state in ('CREATED', 'COMPLETED', 'ABORTED', 'EXPIRED'))
);
create index idx_upload_intents_expiry
    on upload_intents(state, expires_at)
    where state = 'CREATED';

create table internal_request_nonces (
    nonce       uuid primary key,
    expires_at  timestamptz not null,
    created_at  timestamptz not null default now()
);
create index idx_internal_request_nonces_expiry on internal_request_nonces(expires_at);

create table provider_webhook_deliveries (
    id                    uuid primary key,
    processing_job_id     uuid not null references processing_jobs(id) on delete cascade,
    provider_request_id   varchar(255) not null,
    payload_digest        varchar(64) not null,
    artifact_key          varchar(1024) not null,
    received_at           timestamptz not null default now(),
    constraint uq_provider_webhook_request
        unique (provider_request_id),
    constraint chk_provider_webhook_digest
        check (payload_digest ~ '^[a-f0-9]{64}$')
);
create index idx_provider_webhook_job
    on provider_webhook_deliveries(processing_job_id, received_at);
