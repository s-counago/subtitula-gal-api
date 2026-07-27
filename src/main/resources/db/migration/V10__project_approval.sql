-- Institutional sessions are approved once and then fixed: the approval instant
-- is the audit artifact a secretary files. Null means "not approved yet", which
-- is every existing row and every creator project.
alter table projects
  add column approved_at timestamptz;
