-- PostgreSQL lexical/fuzzy baseline. Both extensions are supported by the
-- managed PostgreSQL target and by the local PostgreSQL 17 image.
create extension if not exists unaccent;
create extension if not exists pg_trgm;

create table search_documents (
    id                  uuid primary key,
    publication_id      uuid not null references publications(id) on delete cascade,
    project_id          uuid not null references projects(id) on delete cascade,
    document_kind       varchar(30) not null,
    source_entity_id    uuid not null,
    evidence_segment_id uuid references evidence_segments(id) on delete restrict,
    display_title       varchar(500) not null,
    display_text        text not null,
    normalized_text     text not null,
    search_vector       tsvector not null,
    public_slug         varchar(160) not null,
    publication_version integer not null,
    session_title       varchar(500) not null,
    organization_id     uuid references organizations(id) on delete set null,
    session_body        varchar(255),
    session_date        date,
    language_code       varchar(20),
    speaker_id          uuid references speakers(id) on delete set null,
    speaker_label       varchar(255),
    agenda_item_id      uuid references agenda_items(id) on delete set null,
    agenda_title        varchar(500),
    document_url        text,
    start_ms            bigint,
    end_ms              bigint,
    content_hash        varchar(64) not null,
    index_version       varchar(80) not null,
    active              boolean not null default true,
    tombstoned_at       timestamptz,
    created_at          timestamptz not null default now(),
    constraint uq_search_document_source
        unique (publication_id, document_kind, source_entity_id),
    constraint chk_search_document_kind check (
        document_kind in ('EVIDENCE', 'TOPIC', 'CONTRIBUTION', 'DECISION', 'DOCUMENT_CHUNK')
    ),
    constraint chk_search_document_times check (
        (start_ms is null and end_ms is null)
        or (start_ms >= 0 and end_ms >= start_ms)
    ),
    constraint chk_search_document_hash
        check (content_hash ~ '^[a-f0-9]{64}$')
);
create index idx_search_documents_vector
    on search_documents using gin(search_vector)
    where active = true;
create index idx_search_documents_trigram
    on search_documents using gin(normalized_text gin_trgm_ops)
    where active = true;
create index idx_search_documents_filters
    on search_documents(active, session_date desc, session_body, document_kind);
create index idx_search_documents_publication
    on search_documents(publication_id, active);
create index idx_search_documents_speaker
    on search_documents(speaker_id, active)
    where speaker_id is not null;
create index idx_search_documents_agenda
    on search_documents(agenda_item_id, active)
    where agenda_item_id is not null;

create table search_query_events (
    id              uuid primary key,
    query_hmac      varchar(64) not null,
    query_length    integer not null,
    result_count    integer not null,
    latency_ms      bigint not null,
    search_mode     varchar(20) not null,
    filters         jsonb not null default '{}'::jsonb,
    created_at      timestamptz not null default now(),
    constraint chk_search_query_length check (query_length between 0 and 300),
    constraint chk_search_query_result_count check (result_count >= 0),
    constraint chk_search_query_latency check (latency_ms >= 0),
    constraint chk_search_query_mode check (search_mode in ('LEXICAL', 'HYBRID'))
);
create index idx_search_query_events_created on search_query_events(created_at);

create table search_click_events (
    id              uuid primary key,
    query_event_id  uuid not null references search_query_events(id) on delete cascade,
    search_document_id uuid not null references search_documents(id) on delete cascade,
    result_rank     integer not null,
    created_at      timestamptz not null default now(),
    constraint chk_search_click_rank check (result_rank > 0)
);
create index idx_search_click_events_query on search_click_events(query_event_id);
