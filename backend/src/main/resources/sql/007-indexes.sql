CREATE INDEX idx_folders_parent_id ON explorer.folders USING btree (parent_id);
CREATE INDEX idx_resources_entid ON explorer.resources USING btree (ent_id);