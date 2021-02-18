DROP SCHEMA IF EXISTS explorer CASCADE;
CREATE SCHEMA explorer;
CREATE TABLE explorer.resource_queue (
    id uuid not null,
    updated_at timestamp without time zone not null,
    payload JSONB NOT NULL,
    PRIMARY KEY (id, updated_at)
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