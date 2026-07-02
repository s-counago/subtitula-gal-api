create table custom_fonts (
    id           uuid primary key,
    user_id      uuid        not null references users(id) on delete cascade,
    family       varchar(255) not null,
    content_type varchar(120) not null,
    size_bytes   bigint      not null,
    bytes        bytea       not null,
    created_at   timestamptz not null default now()
);
create index idx_custom_fonts_user on custom_fonts(user_id);
