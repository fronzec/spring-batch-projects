-- =========================================================
-- V6: Ticket bundle tables
-- generated_bundles: tracks the ZIP bundle produced per event
-- =========================================================

CREATE TABLE generated_bundles (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    event_id        BIGINT        NOT NULL,
    storage_type    VARCHAR(16)   NOT NULL DEFAULT 'LOCAL',
    storage_path    VARCHAR(1024) NOT NULL,
    checksum_sha256 CHAR(64)      NOT NULL,
    file_size_bytes BIGINT        NOT NULL,
    ticket_count    INT           NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_generated_bundles_event UNIQUE (event_id)
);
