-- =========================================================
-- V8: Partitioned harvester tables
-- usage_record:   frozen source table; one row per billable event
-- billing_charge: output table; one billing charge per usage_record row
--
-- Design notes:
--   - usage_record is FROZEN: no processed flag, no mutations during job execution
--   - billing_charge.source_id has a UNIQUE constraint to enforce 1:1 idempotency
--   - cost = units * rate (integer minor units, e.g. millis or cents per usage unit)
--   - job_execution_id is nullable; populated by the writer for audit tracing
-- =========================================================

CREATE TABLE usage_record (
    id            BIGINT        AUTO_INCREMENT PRIMARY KEY,
    subscriber_id BIGINT        NOT NULL,
    units         BIGINT        NOT NULL,
    rate          BIGINT        NOT NULL,
    recorded_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE billing_charge (
    id               BIGINT    AUTO_INCREMENT PRIMARY KEY,
    source_id        BIGINT    NOT NULL,
    subscriber_id    BIGINT    NOT NULL,
    units            BIGINT    NOT NULL,
    rate             BIGINT    NOT NULL,
    cost             BIGINT    NOT NULL,
    job_execution_id BIGINT    NULL,
    recorded_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_billing_charge_source UNIQUE (source_id)
);
