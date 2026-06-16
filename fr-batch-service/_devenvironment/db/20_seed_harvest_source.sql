-- =========================================================
-- Dev seed: harvest_source demo rows
-- For local development only — integration tests seed their own data via JDBC
-- Run AFTER V7__fault_tolerant_harvester_tables.sql has been applied
--
-- Mix of scenarios:
--   - 10 normal rows  (no poison, no transient, no abort)
--   -  2 poison rows  (poison_flag=TRUE  → PoisonItemException → dead-letter SKIP)
--   -  3 transient rows (transient_fail_until_attempt=1,2,3 → simulates retry threshold)
--   -  1 abort row    (abort_flag=TRUE → AbortJobException → step FAILS; clear before restart demo)
-- =========================================================

INSERT INTO harvest_source (payload, poison_flag, transient_fail_until_attempt, abort_flag, processed)
VALUES
    -- Normal rows (processed in a single happy-path run)
    ('{"type":"order","id":"ORD-001","amount":120.00}', FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-002","amount":75.50}',  FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-003","amount":200.00}', FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-004","amount":34.99}',  FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-005","amount":89.00}',  FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-006","amount":15.00}',  FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-007","amount":450.00}', FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-008","amount":300.00}', FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-009","amount":22.75}',  FALSE, 0, FALSE, FALSE),
    ('{"type":"order","id":"ORD-010","amount":67.30}',  FALSE, 0, FALSE, FALSE),

    -- Poison rows (poison_flag=TRUE → skipped and dead-lettered with failure_type='SKIP')
    ('{"type":"poison","id":"PSN-001","reason":"malformed"}', TRUE,  0, FALSE, FALSE),
    ('{"type":"poison","id":"PSN-002","reason":"corrupt"}',   TRUE,  0, FALSE, FALSE),

    -- Transient rows (simulate progressive retry thresholds; succeed after N retries)
    ('{"type":"transient","id":"TRN-001","threshold":1}', FALSE, 1, FALSE, FALSE),
    ('{"type":"transient","id":"TRN-002","threshold":2}', FALSE, 2, FALSE, FALSE),
    ('{"type":"transient","id":"TRN-003","threshold":3}', FALSE, 3, FALSE, FALSE),

    -- Abort row (abort_flag=TRUE → AbortJobException; triggers job FAILURE for restart demo)
    -- Clear this row's abort_flag before re-launching with identical params to demo restart.
    ('{"type":"abort","id":"ABT-001","note":"clear abort_flag before restart demo"}',
     FALSE, 0, TRUE, FALSE);
