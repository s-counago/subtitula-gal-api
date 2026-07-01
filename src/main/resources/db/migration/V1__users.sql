create table users (
    id             uuid primary key,
    email          varchar(320) not null unique,
    email_verified boolean      not null default false,
    password_hash  varchar(100),
    display_name   varchar(120) not null,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now()
);
create unique index idx_users_email_lower on users (lower(email));
