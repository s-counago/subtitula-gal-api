create table oauth_accounts (
    id               uuid primary key,
    user_id          uuid not null references users(id) on delete cascade,
    provider         varchar(20) not null,
    provider_user_id varchar(255) not null,
    created_at       timestamptz not null default now(),
    unique (provider, provider_user_id)
);
create index idx_oauth_user on oauth_accounts(user_id);
