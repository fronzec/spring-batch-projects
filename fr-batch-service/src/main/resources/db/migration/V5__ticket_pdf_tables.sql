-- =========================================================
-- V5: Ticket PDF tables
-- event_tickets: source rows for PDF generation
-- generated_files: tracks each generated PDF file
-- =========================================================

CREATE TABLE event_tickets (
    id             BIGINT        AUTO_INCREMENT PRIMARY KEY,
    event_id       BIGINT        NOT NULL,
    ticket_code    VARCHAR(64)   NOT NULL,
    holder_name    VARCHAR(255)  NOT NULL,
    event_name     VARCHAR(255)  NOT NULL,
    event_location VARCHAR(255)  NULL,
    seat           VARCHAR(64)   NULL,
    event_datetime TIMESTAMP     NOT NULL,
    processed      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_event_tickets_ticket_code UNIQUE (ticket_code)
);

CREATE INDEX idx_event_tickets_processed ON event_tickets (processed);

CREATE TABLE generated_files (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT        NOT NULL,
    storage_type    VARCHAR(16)   NOT NULL DEFAULT 'LOCAL',
    storage_path    VARCHAR(1024) NOT NULL,
    checksum_sha256 CHAR(64)      NOT NULL,
    file_size_bytes BIGINT        NOT NULL,
    generated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_generated_files_ticket
        FOREIGN KEY (ticket_id) REFERENCES event_tickets (id),
    CONSTRAINT uk_generated_files_ticket UNIQUE (ticket_id)
);
