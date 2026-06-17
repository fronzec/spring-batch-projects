-- =========================================================
-- Dev seed: usage_record rows for the partitioned-harvester demo
-- Run AFTER V8__partitioned_harvester_tables.sql has been applied
--
-- 24 contiguous rows (ids 1-24), grouped into four cost blocks of 6 rows
-- each (by subscriber) purely to give the billing total some variety.
--
-- Cost blocks (by id — these are NOT partition boundaries):
--   ids  1- 6  subscriber_id=100  units=10  rate=5  cost=50 each
--   ids  7-12  subscriber_id=101  units=20  rate=3  cost=60 each
--   ids 13-18  subscriber_id=102  units=5   rate=8  cost=40 each
--   ids 19-24  subscriber_id=103  units=15  rate=4  cost=60 each
-- Expected total: 6*50 + 6*60 + 6*40 + 6*60 = 1260 (partition-independent)
--
-- How IdRangePartitioner actually splits this with gridSize=4:
--   MIN=1, MAX=24, span=23, rangeSize=floor(23/4)=5
--   partition0 -> [1, 5]   (5 rows)
--   partition1 -> [6, 10]  (5 rows)
--   partition2 -> [11, 15] (5 rows)
--   partition3 -> [16, 24] (9 rows — last partition absorbs the remainder)
-- Partition ranges deliberately do NOT align with the cost blocks above:
-- partitioning is orthogonal to the data's grouping, and Sigma(cost) is
-- unaffected by how rows are split across workers.
-- =========================================================

INSERT INTO usage_record (subscriber_id, units, rate) VALUES
    -- Cost block (ids 1-6): subscriber 100, units=10, rate=5 -> cost=50 each
    (100, 10, 5),
    (100, 10, 5),
    (100, 10, 5),
    (100, 10, 5),
    (100, 10, 5),
    (100, 10, 5),

    -- Cost block (ids 7-12): subscriber 101, units=20, rate=3 -> cost=60 each
    (101, 20, 3),
    (101, 20, 3),
    (101, 20, 3),
    (101, 20, 3),
    (101, 20, 3),
    (101, 20, 3),

    -- Cost block (ids 13-18): subscriber 102, units=5, rate=8 -> cost=40 each
    (102, 5, 8),
    (102, 5, 8),
    (102, 5, 8),
    (102, 5, 8),
    (102, 5, 8),
    (102, 5, 8),

    -- Cost block (ids 19-24): subscriber 103, units=15, rate=4 -> cost=60 each
    (103, 15, 4),
    (103, 15, 4),
    (103, 15, 4),
    (103, 15, 4),
    (103, 15, 4),
    (103, 15, 4);
