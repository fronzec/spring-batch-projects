-- =========================================================
-- Dev seed: usage_record rows for the partitioned-harvester demo
-- Run AFTER V8__partitioned_harvester_tables.sql has been applied
--
-- 24 contiguous rows (ids 1–24) arranged so gridSize=4 yields
-- exactly 6 rows per partition.
--
-- Partition breakdown (gridSize=4, rangeSize=6):
--   Partition 0: ids  1– 6  subscriber_id=100  units=10  rate=5  cost=50 each
--   Partition 1: ids  7–12  subscriber_id=101  units=20  rate=3  cost=60 each
--   Partition 2: ids 13–18  subscriber_id=102  units=5   rate=8  cost=40 each
--   Partition 3: ids 19–24  subscriber_id=103  units=15  rate=4  cost=60 each
--
-- Expected total: 6*50 + 6*60 + 6*40 + 6*60 = 300 + 360 + 240 + 360 = 1260
-- =========================================================

INSERT INTO usage_record (subscriber_id, units, rate) VALUES
    -- Partition 0 (ids 1–6): subscriber 100, units=10, rate=5 → cost=50 each
    (100, 10, 5),
    (100, 10, 5),
    (100, 10, 5),
    (100, 10, 5),
    (100, 10, 5),
    (100, 10, 5),

    -- Partition 1 (ids 7–12): subscriber 101, units=20, rate=3 → cost=60 each
    (101, 20, 3),
    (101, 20, 3),
    (101, 20, 3),
    (101, 20, 3),
    (101, 20, 3),
    (101, 20, 3),

    -- Partition 2 (ids 13–18): subscriber 102, units=5, rate=8 → cost=40 each
    (102, 5, 8),
    (102, 5, 8),
    (102, 5, 8),
    (102, 5, 8),
    (102, 5, 8),
    (102, 5, 8),

    -- Partition 3 (ids 19–24): subscriber 103, units=15, rate=4 → cost=60 each
    (103, 15, 4),
    (103, 15, 4),
    (103, 15, 4),
    (103, 15, 4),
    (103, 15, 4),
    (103, 15, 4);
