-- Automatic agenda alignment and evidence-backed assisted session guides.
-- All generated public-facing objects are kept separate from the transcript and
-- must have at least one validated guide_evidence_link before they can be shown.

create table agenda_items (
    id                  uuid primary key,
    project_id          uuid not null references projects(id) on delete cascade,
    ordinal             integer not null,
    external_identifier varchar(120),
    title               varchar(500) not null,
    description         text,
    source              varchar(30) not null,
    visibility          varchar(20) not null default 'PUBLIC',
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    version             bigint not null default 0,
    constraint uq_agenda_item_ordinal unique (project_id, ordinal),
    constraint chk_agenda_item_ordinal check (ordinal >= 0),
    constraint chk_agenda_item_title check (length(btrim(title)) > 0),
    constraint chk_agenda_item_source check (
        source in ('MANUAL', 'PASTE', 'DOCUMENT_IMPORT', 'OFFICIAL_URL')
    ),
    constraint chk_agenda_item_visibility check (
        visibility in ('PRIVATE', 'PUBLIC')
    )
);
create index idx_agenda_items_project on agenda_items(project_id, ordinal);

create table agenda_alignments (
    id                     uuid primary key,
    transcript_revision_id uuid not null references transcript_revisions(id) on delete cascade,
    agenda_item_id         uuid not null references agenda_items(id) on delete cascade,
    occurrence             integer not null default 0,
    start_segment_id       uuid not null references evidence_segments(id) on delete cascade,
    end_segment_id         uuid not null references evidence_segments(id) on delete cascade,
    signals                jsonb not null default '{}'::jsonb,
    alignment_state        varchar(30) not null,
    requires_human_check   boolean not null default false,
    algorithm_version      varchar(120) not null,
    revisited              boolean not null default false,
    checked_by             uuid references users(id) on delete set null,
    checked_at             timestamptz,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint not null default 0,
    constraint uq_agenda_alignment_occurrence
        unique (transcript_revision_id, agenda_item_id, occurrence),
    constraint chk_agenda_alignment_occurrence check (occurrence >= 0),
    constraint chk_agenda_alignment_state check (
        alignment_state in ('AUTOMATIC', 'CONFIRMED', 'ADJUSTED', 'UNRESOLVED')
    )
);
create index idx_agenda_alignments_revision
    on agenda_alignments(transcript_revision_id, start_segment_id);
create index idx_agenda_alignments_checks
    on agenda_alignments(transcript_revision_id, requires_human_check)
    where requires_human_check = true;

create table session_guides (
    id                     uuid primary key,
    project_id             uuid not null references projects(id) on delete cascade,
    transcript_revision_id uuid not null references transcript_revisions(id) on delete restrict,
    version_number         integer not null,
    schema_version         varchar(40) not null,
    generation_model       varchar(160) not null,
    prompt_version         varchar(120) not null,
    guide_state            varchar(30) not null,
    generator_content_hash varchar(64) not null,
    raw_artifact_key       varchar(1024),
    created_at             timestamptz not null default now(),
    confirmed_by           uuid references users(id) on delete set null,
    confirmed_at           timestamptz,
    version                bigint not null default 0,
    constraint uq_session_guide_version unique (project_id, version_number),
    constraint uq_session_guide_hash unique (project_id, generator_content_hash),
    constraint chk_session_guide_version check (version_number > 0),
    constraint chk_session_guide_state check (
        guide_state in ('GENERATED', 'EXCEPTIONS', 'READY', 'PUBLISHED', 'SUPERSEDED')
    ),
    constraint chk_session_guide_hash
        check (generator_content_hash ~ '^[a-f0-9]{64}$')
);
create index idx_session_guides_project
    on session_guides(project_id, version_number desc);

create table guide_topics (
    id               uuid primary key,
    guide_id         uuid not null references session_guides(id) on delete cascade,
    ordinal          integer not null,
    title            varchar(500) not null,
    neutral_summary  text not null,
    aliases          jsonb not null default '[]'::jsonb,
    agenda_item_id   uuid references agenda_items(id) on delete set null,
    start_segment_id uuid not null references evidence_segments(id) on delete restrict,
    end_segment_id   uuid not null references evidence_segments(id) on delete restrict,
    generation_state varchar(30) not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    version          bigint not null default 0,
    constraint uq_guide_topic_ordinal unique (guide_id, ordinal),
    constraint chk_guide_topic_ordinal check (ordinal >= 0),
    constraint chk_guide_topic_title check (length(btrim(title)) > 0),
    constraint chk_guide_topic_summary check (length(btrim(neutral_summary)) > 0),
    constraint chk_guide_topic_generation_state check (
        generation_state in ('AUTOMATIC', 'CONFIRMED', 'EDITED')
    )
);
create index idx_guide_topics_guide on guide_topics(guide_id, ordinal);

create table guide_contributions (
    id                 uuid primary key,
    topic_id           uuid not null references guide_topics(id) on delete cascade,
    ordinal            integer not null,
    speaker_id         uuid references speakers(id) on delete set null,
    contribution_kind  varchar(30) not null,
    neutral_summary    text not null,
    generation_state   varchar(30) not null,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    version            bigint not null default 0,
    constraint uq_guide_contribution_ordinal unique (topic_id, ordinal),
    constraint chk_guide_contribution_ordinal check (ordinal >= 0),
    constraint chk_guide_contribution_summary check (length(btrim(neutral_summary)) > 0),
    constraint chk_guide_contribution_kind check (
        contribution_kind in (
            'QUESTION', 'PROPOSAL', 'EXPLANATION', 'REPLY', 'OBJECTION',
            'SUPPORT', 'PROCEDURAL', 'OTHER'
        )
    ),
    constraint chk_guide_contribution_generation_state check (
        generation_state in ('AUTOMATIC', 'CONFIRMED', 'EDITED')
    )
);
create index idx_guide_contributions_topic
    on guide_contributions(topic_id, ordinal);
create index idx_guide_contributions_speaker
    on guide_contributions(speaker_id);

create table guide_decisions (
    id                    uuid primary key,
    topic_id              uuid not null references guide_topics(id) on delete cascade,
    agenda_item_id        uuid references agenda_items(id) on delete set null,
    ordinal               integer not null,
    neutral_description   text not null,
    decision_status       varchar(30) not null,
    motion                text,
    result                varchar(500),
    vote_details          jsonb,
    confirmed_by          uuid references users(id) on delete set null,
    confirmed_at          timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    version               bigint not null default 0,
    constraint uq_guide_decision_ordinal unique (topic_id, ordinal),
    constraint chk_guide_decision_ordinal check (ordinal >= 0),
    constraint chk_guide_decision_description
        check (length(btrim(neutral_description)) > 0),
    constraint chk_guide_decision_status check (
        decision_status in ('CANDIDATE', 'CONFIRMED', 'DOCUMENT_SUPPORTED', 'OMITTED')
    )
);
create index idx_guide_decisions_topic on guide_decisions(topic_id, ordinal);

create table guide_evidence_links (
    id                  uuid primary key,
    guide_id            uuid not null references session_guides(id) on delete cascade,
    subject_type        varchar(30) not null,
    subject_id          uuid not null,
    evidence_segment_id uuid not null references evidence_segments(id) on delete restrict,
    link_purpose        varchar(80) not null default 'SUPPORT',
    ordinal             integer not null,
    created_at          timestamptz not null default now(),
    constraint uq_guide_evidence_link
        unique (subject_type, subject_id, evidence_segment_id, link_purpose),
    constraint chk_guide_evidence_subject
        check (subject_type in ('TOPIC', 'CONTRIBUTION', 'DECISION')),
    constraint chk_guide_evidence_ordinal check (ordinal >= 0)
);
create index idx_guide_evidence_subject
    on guide_evidence_links(subject_type, subject_id, ordinal);
create index idx_guide_evidence_segment
    on guide_evidence_links(evidence_segment_id);
