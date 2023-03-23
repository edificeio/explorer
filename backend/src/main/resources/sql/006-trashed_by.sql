ALTER TABLE explorer.resources  ADD COLUMN trashed_by JSONB not null default '{}';
