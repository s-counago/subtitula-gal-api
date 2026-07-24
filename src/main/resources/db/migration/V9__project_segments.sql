-- Per-segment positioning & styling. Both nullable: a missing base_box means the
-- client's default placement, a missing/empty segments list means no overrides.
alter table projects
  add column base_box jsonb,
  add column segments jsonb;
