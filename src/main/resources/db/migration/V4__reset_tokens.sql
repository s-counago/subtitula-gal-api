create table password_reset_tokens (
    id         uuid primary key,
    user_id    uuid not null references users(id) on delete cascade,
    token_hash char(64) not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now()
);
create index idx_prt_user on password_reset_tokens(user_id);
