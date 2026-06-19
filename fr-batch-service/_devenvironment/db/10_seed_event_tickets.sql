-- =========================================================
-- Dev seed: event_tickets demo rows
-- For local development only — integration tests seed their own data via JDBC
-- Run AFTER V5__ticket_pdf_tables.sql has been applied
--
-- Idempotent: the WHERE NOT EXISTS guard makes a re-run a no-op once the table
-- has data, so `task dev` / repeated seeding never hits duplicate-key errors.
-- =========================================================

INSERT INTO event_tickets (event_id, ticket_code, holder_name, event_name, event_location, seat, event_datetime, processed)
SELECT * FROM (VALUES
    -- Event 1: Tech Conference 2024
    ROW(1, 'TC2024-001', 'Alice Johnson',    'Tech Conference 2024', 'Convention Center Hall A', 'A-01', '2024-09-15 09:00:00', FALSE),
    ROW(1, 'TC2024-002', 'Bob Martinez',     'Tech Conference 2024', 'Convention Center Hall A', 'A-02', '2024-09-15 09:00:00', FALSE),
    ROW(1, 'TC2024-003', 'Carol White',      'Tech Conference 2024', 'Convention Center Hall A', 'B-01', '2024-09-15 09:00:00', FALSE),
    ROW(1, 'TC2024-004', 'David Brown',      'Tech Conference 2024', 'Convention Center Hall A', 'B-02', '2024-09-15 09:00:00', FALSE),

    -- Event 2: Spring Music Fest
    ROW(2, 'SMF2024-001', 'Emma Davis',      'Spring Music Fest',    'Open Air Stadium',        'GA-100', '2024-05-20 18:00:00', FALSE),
    ROW(2, 'SMF2024-002', 'Frank Garcia',    'Spring Music Fest',    'Open Air Stadium',        'GA-101', '2024-05-20 18:00:00', FALSE),
    ROW(2, 'SMF2024-003', 'Grace Lee',       'Spring Music Fest',    'Open Air Stadium',        'VIP-01', '2024-05-20 18:00:00', FALSE),
    ROW(2, 'SMF2024-004', 'Henry Wilson',    'Spring Music Fest',    'Open Air Stadium',        'VIP-02', '2024-05-20 18:00:00', FALSE),

    -- Event 3: Annual Gala Dinner (no seat, no location)
    ROW(3, 'AGD2024-001', 'Irene Thompson',  'Annual Gala Dinner',   NULL,                      NULL,     '2024-12-01 20:00:00', FALSE),
    ROW(3, 'AGD2024-002', 'Jack Robinson',   'Annual Gala Dinner',   NULL,                      NULL,     '2024-12-01 20:00:00', FALSE)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM event_tickets);
