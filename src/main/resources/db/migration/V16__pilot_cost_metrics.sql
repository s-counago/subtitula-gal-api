alter table search_query_events
    add column estimated_ai_cost_microunits bigint not null default 0,
    add column cost_currency varchar(3) not null default 'USD',
    add constraint chk_search_query_estimated_cost
        check (estimated_ai_cost_microunits >= 0),
    add constraint chk_search_query_cost_currency
        check (cost_currency ~ '^[A-Z]{3}$');

create index idx_search_query_events_mode_created
    on search_query_events(search_mode, created_at);
