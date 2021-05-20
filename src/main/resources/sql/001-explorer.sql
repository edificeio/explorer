DROP SCHEMA IF EXISTS explorer CASCADE;
CREATE SCHEMA explorer;
CREATE TABLE explorer.scripts (
	filename VARCHAR(255) NOT NULL PRIMARY KEY,
	passed TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE TABLE explorer.resource_queue (
    id SERIAL PRIMARY KEY,
    id_resource VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    resource_action VARCHAR(16) NOT NULL CHECK (resource_action IN ('Upsert', 'Delete', 'Audience')),
    payload JSONB NOT NULL,
    attempted_at TIMESTAMP WITHOUT TIME ZONE,
    attempted_count INTEGER NOT NULL DEFAULT 0,
    attempt_status SMALLINT NOT NULL DEFAULT 0,
    priority INTEGER NOT NULL
);
CREATE TABLE explorer.resource_queue_causes (
    id BIGINT PRIMARY KEY,
    id_resource VARCHAR(128) NOT NULL,
    attempt_reason TEXT,
    attempted_at TIMESTAMP WITHOUT TIME ZONE
);
CREATE TABLE explorer.share_subjects (
    id VARCHAR(200) not null PRIMARY KEY,
    hash_algorithm VARCHAR(10) NOT NULL
);
CREATE TABLE explorer.share_groups (
    id VARCHAR(100) NOT NULL,
    displayName TEXT,
    share_subject_id TEXT NOT NULL,
    PRIMARY KEY(id, share_subject_id),
    CONSTRAINT fk_share_subjects FOREIGN KEY(share_subject_id) REFERENCES explorer.share_subjects(id) ON DELETE CASCADE INITIALLY DEFERRED
);
CREATE TABLE explorer.share_users (
    id VARCHAR(100) NOT NULL,
    displayName TEXT,
    share_subject_id TEXT NOT NULL,
    PRIMARY KEY(id, share_subject_id),
    CONSTRAINT fk_share_subjects FOREIGN KEY(share_subject_id) REFERENCES explorer.share_subjects(id) ON DELETE CASCADE INITIALLY DEFERRED
);

CREATE TABLE explorer.folders (
    id SERIAL,
    name TEXT,
    application VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    parent_id INTEGER,
    creator_id VARCHAR(100) NOT NULL,
    creator_name TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    trashed BOOLEAN DEFAULT FALSE,
    trashedAt TIMESTAMP,
    PRIMARY KEY(id),
    CONSTRAINT fk_folders FOREIGN KEY(parent_id) REFERENCES explorer.folders(id) ON DELETE SET NULL INITIALLY DEFERRED
);
CREATE INDEX idx_folder_parent_id ON explorer.folders(parent_id);

CREATE TABLE explorer.resources (
    id SERIAL,
    name TEXT,
    creator_id VARCHAR(100) NOT NULL,
    application VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    ent_id VARCHAR(100) NOT NULL,
    resource_unique_id TEXT NOT NULL,
    trashed BOOLEAN DEFAULT FALSE,
    trashedAt TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    shared JSONB,
    PRIMARY KEY(id)
);
CREATE UNIQUE INDEX idx_resources_ent_id ON explorer.resources(resource_unique_id);

CREATE TABLE explorer.folder_resources (
    folder_id BIGINT NULL,
    resource_id BIGINT NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    PRIMARY KEY(folder_id, resource_id, user_id),
    CONSTRAINT fk_folder_resources_folderid FOREIGN KEY(folder_id) REFERENCES explorer.folders(id) ON DELETE CASCADE INITIALLY DEFERRED,
    CONSTRAINT fk_folders_resources_resourceid FOREIGN KEY(resource_id) REFERENCES explorer.resources(id) ON DELETE CASCADE INITIALLY DEFERRED
);
CREATE INDEX idx_folder_resources_folderid ON explorer.folder_resources(folder_id);
CREATE INDEX idx_folder_resources_resourceid ON explorer.folder_resources(resource_id);
CREATE UNIQUE INDEX idx_folder_resources_resourceid_userid ON explorer.folder_resources(user_id,resource_id);