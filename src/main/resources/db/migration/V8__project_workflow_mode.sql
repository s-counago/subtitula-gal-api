alter table projects
  add column workflow_mode varchar(20) not null default 'creator';

alter table projects
  add constraint chk_projects_workflow_mode
  check (workflow_mode in ('creator', 'institution'));
