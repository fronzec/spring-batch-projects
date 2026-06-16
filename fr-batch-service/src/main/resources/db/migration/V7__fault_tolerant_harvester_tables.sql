-- =========================================================
-- V7: Fault-tolerant harvester tables
-- harvest_source:     input records for the harvester job
-- harvest_dead_letter: audit log for skipped / retry-exhausted items
-- =========================================================

CREATE TABLE harvest_source (
    id                        BIGINT        AUTO_INCREMENT PRIMARY KEY,
    payload                   VARCHAR(2048) NOT NULL,
    poison_flag               BOOLEAN       NOT NULL DEFAULT FALSE,
    transient_fail_until_attempt INT         NOT NULL DEFAULT 0 CHECK (transient_fail_until_attempt >= 0),
    abort_flag                BOOLEAN       NOT NULL DEFAULT FALSE,
    processed                 BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE harvest_dead_letter (
    id               BIGINT        AUTO_INCREMENT PRIMARY KEY,
    source_id        BIGINT        NULL,
    raw_payload      VARCHAR(2048) NULL,
    failure_phase    VARCHAR(16)   NOT NULL,
    failure_type     VARCHAR(16)   NOT NULL,
    exception_class  VARCHAR(512)  NOT NULL,
    exception_msg    VARCHAR(2048) NULL,
    attempt_count    INT           NOT NULL DEFAULT 1 CHECK (attempt_count >= 0),
    job_execution_id BIGINT        NOT NULL,
    recorded_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
