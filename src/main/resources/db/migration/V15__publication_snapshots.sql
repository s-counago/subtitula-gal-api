-- Immutable publication snapshots. Draft/project changes never alter evidence
-- already exposed by a published version.

create table project_documents (
    id                     uuid primary key,
    project_id             uuid not null references projects(id) on delete cascade,
    document_type          varchar(30) not null,
    official_url           text,
    private_object_key     varchar(1024),
    title                  varchar(500) not null,
    issuing_body           varchar(500),
    document_date          date,
    checksum_sha256        varchar(64),
    visibility             varchar(20) not null default 'PRIVATE',
    provenance             varchar(40) not null,
    publication_permission boolean not null default false,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint not null default 0,
    constraint chk_project_document_type check (
        document_type in ('AGENDA', 'NOTICE', 'MINUTES', 'PROPOSAL', 'AGREEMENT', 'OTHER')
    ),
    constraint chk_project_document_location check (
        official_url is not null or private_object_key is not null
    ),
    constraint chk_project_document_visibility check (
        visibility in ('PRIVATE', 'PUBLIC')
    ),
    constraint chk_project_document_provenance check (
        provenance in ('OPERATOR', 'OFFICIAL_URL', 'IMPORTED')
    ),
    constraint chk_project_document_checksum check (
        checksum_sha256 is null or checksum_sha256 ~ '^[a-f0-9]{64}$'
    )
);
create index idx_project_documents_project
    on project_documents(project_id, document_date, title);

create table publications (
    id                     uuid primary key,
    project_id             uuid not null references projects(id) on delete restrict,
    organization_id        uuid references organizations(id) on delete set null,
    public_slug            varchar(160) not null,
    version_number         integer not null,
    transcript_revision_id uuid not null references transcript_revisions(id) on delete restrict,
    guide_id               uuid references session_guides(id) on delete restrict,
    recording_id           uuid not null references recordings(id) on delete restrict,
    publication_state      varchar(30) not null,
    title                  varchar(500) not null,
    language_code          varchar(20),
    session_date           date,
    session_body           varchar(255),
    location               varchar(255),
    session_type           varchar(40),
    correction_note        varchar(1000),
    responsible_publisher  uuid references users(id) on delete set null,
    created_at             timestamptz not null default now(),
    published_at           timestamptz,
    superseded_at          timestamptz,
    withdrawn_at           timestamptz,
    version                bigint not null default 0,
    constraint uq_publication_project_version unique (project_id, version_number),
    constraint uq_publication_slug_version unique (public_slug, version_number),
    constraint chk_publication_version check (version_number > 0),
    constraint chk_publication_slug
        check (public_slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint chk_publication_state check (
        publication_state in ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'WITHDRAWN')
    )
);
create unique index uq_publications_active_slug
    on publications(public_slug)
    where publication_state = 'PUBLISHED';
create unique index uq_publications_active_project
    on publications(project_id)
    where publication_state = 'PUBLISHED';
create index idx_publications_archive
    on publications(publication_state, session_date desc, published_at desc);

create table publication_documents (
    id             uuid primary key,
    publication_id uuid not null references publications(id) on delete cascade,
    document_id    uuid not null references project_documents(id) on delete restrict,
    ordinal        integer not null,
    constraint uq_publication_document unique (publication_id, document_id),
    constraint uq_publication_document_ordinal unique (publication_id, ordinal),
    constraint chk_publication_document_ordinal check (ordinal >= 0)
);
