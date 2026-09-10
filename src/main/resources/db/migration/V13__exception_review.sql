create table review_sessions (
    id                     uuid primary key,
    project_id             uuid not null references projects(id) on delete cascade,
    transcript_revision_id uuid not null references transcript_revisions(id) on delete cascade,
    reviewer_id            uuid not null references users(id) on delete cascade,
    started_at             timestamptz not null default now(),
    last_activity_at       timestamptz not null default now(),
    active_duration_ms     bigint not null default 0,
    resolved_count         integer not null default 0,
    dismissed_count        integer not null default 0,
    manual_edit_count      integer not null default 0,
    completed_at           timestamptz,
    version                bigint not null default 0,
    constraint chk_review_session_duration check (active_duration_ms >= 0),
    constraint chk_review_session_counts check (
        resolved_count >= 0 and dismissed_count >= 0 and manual_edit_count >= 0
    )
);
create unique index uq_review_sessions_active_reviewer
    on review_sessions(project_id, reviewer_id)
    where completed_at is null;
create index idx_review_sessions_project
    on review_sessions(project_id, started_at);
