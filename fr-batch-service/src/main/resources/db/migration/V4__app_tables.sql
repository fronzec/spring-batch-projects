-- =========================================================
-- V4: Application tables (from _devenvironment/db/00_create_schema.sql)
-- Tables needed by job1 (persons) and job2 (dispatched_group, persons_v2)
-- =========================================================

CREATE TABLE persons (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(50)  NOT NULL,
    last_name  VARCHAR(50)  NULL,
    profession VARCHAR(30)  NOT NULL,
    email      VARCHAR(50)  NOT NULL,
    processed  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_persons_processed ON persons (processed);

CREATE TABLE dispatched_group (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid_v4          CHAR(36)                              NOT NULL,
    dispatch_status  VARCHAR(10) DEFAULT 'UNKNOWN'         NOT NULL,
    records_included INT       DEFAULT 0                    NOT NULL,
    created_at       DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       DATETIME    DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_dispatched_group_uuid UNIQUE (uuid_v4)
);

CREATE TABLE persons_v2 (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_date          DATE                                    NOT NULL,
    first_name             VARCHAR(50)                             NOT NULL,
    last_name              VARCHAR(50)                             NOT NULL,
    email                  VARCHAR(50)                             NOT NULL,
    profession             CHAR(15)                                NOT NULL,
    salary                 DECIMAL(19, 2) DEFAULT 0.00             NOT NULL,
    uuid_v4                CHAR(36)                                NOT NULL,
    created_at             DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at             DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    fk_dispatched_group_id BIGINT                                   NULL,
    CONSTRAINT uk_persons_v2_uuid UNIQUE (uuid_v4),
    CONSTRAINT fk_persons_v2_dispatched_group
        FOREIGN KEY (fk_dispatched_group_id) REFERENCES dispatched_group (id)
);

CREATE INDEX idx_persons_v2_snapshot_profession ON persons_v2 (snapshot_date, profession);
