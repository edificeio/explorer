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