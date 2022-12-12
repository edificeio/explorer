ALTER TABLE explorer.resources  ADD COLUMN version int;
ALTER TABLE explorer.resources  ADD COLUMN ingest_job_state VARCHAR(20);
ALTER TABLE explorer.folders  ADD COLUMN version int;
ALTER TABLE explorer.folders  ADD COLUMN ingest_job_state VARCHAR(20);
