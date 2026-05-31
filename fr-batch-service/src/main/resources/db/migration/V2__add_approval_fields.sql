-- =========================================================
-- V2: Add approval workflow fields to job_definitions
-- =========================================================

ALTER TABLE job_definitions ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE job_definitions ADD COLUMN approved_by VARCHAR(100);
ALTER TABLE job_definitions ADD COLUMN approved_at TIMESTAMP NULL;

-- Backward compatibility: existing definitions are implicitly trusted
UPDATE job_definitions SET approval_status = 'APPROVED', approved_by = 'system', approved_at = NOW();
