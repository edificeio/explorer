DROP SCHEMA IF EXISTS explorer CASCADE;
CREATE SCHEMA explorer;
CREATE TABLE explorer.resource_queue (
    id SERIAL PRIMARY KEY,
    id_resource VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    resource_action VARCHAR(16) NOT NULL CHECK (resource_action IN ('create', 'update', 'delete')),
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
    id TEXT not null PRIMARY KEY
);
CREATE TABLE explorer.share_groups (
    id VARCHAR(64) not null PRIMARY KEY,
    displayName TEXT,
    share_subject_id TEXT NOT NULL,
    CONSTRAINT fk_share_subjects FOREIGN KEY(share_subject_id) REFERENCES explorer.share_subjects(id)
);
CREATE TABLE explorer.share_users (
    id VARCHAR(64) not null PRIMARY KEY,
    displayName TEXT,
    share_subject_id TEXT NOT NULL,
    CONSTRAINT fk_share_subjects FOREIGN KEY(share_subject_id) REFERENCES explorer.share_subjects(id)
);