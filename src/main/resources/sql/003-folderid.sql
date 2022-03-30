ALTER TABLE explorer.folders  ADD COLUMN ent_id VARCHAR(100);
ALTER TABLE explorer.folders  ADD COLUMN parent_ent_id VARCHAR(100);
ALTER TABLE explorer.folders ADD CONSTRAINT ent_id_uniq UNIQUE (ent_id);
CREATE INDEX idx_folders_parent_ent ON explorer.folders (parent_id, parent_ent_id);
CREATE INDEX idx_resources_parent_ent ON explorer.folder_resources (folder_id, folder_ent_id);