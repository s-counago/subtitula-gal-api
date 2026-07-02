create table projects (
    id           uuid primary key,
    user_id      uuid        not null references users(id) on delete cascade,
    name         varchar(255) not null,
    language     varchar(20),
    duration_sec double precision not null default 0,
    words        jsonb       not null default '[]'::jsonb,
    style        jsonb,
    speed_factor double precision not null default 1.0,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create index idx_projects_user on projects(user_id);
