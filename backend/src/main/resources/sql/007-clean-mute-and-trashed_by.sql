ALTER TABLE explorer.resources ALTER COLUMN muted_by DROP not null;
ALTER TABLE explorer.resources ALTER COLUMN muted_by DROP default;
ALTER TABLE explorer.resources ALTER COLUMN trashed_by DROP not null;
ALTER TABLE explorer.resources ALTER COLUMN trashed_by DROP default;