ALTER TABLE explorer.resources  ADD COLUMN muted_by JSONB not null default '{}';
