package com.fronzec.plugins.partitionedharvester.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes {@link BillingCharge} records to the {@code billing_charge} table using a guarded
 * {@code INSERT … WHERE NOT EXISTS} pattern for idempotency.
 *
 * <h3>Idempotency design</h3>
 * <p>The {@code billing_charge} table has a UNIQUE constraint on {@code source_id}
 * (the primary key of the originating {@code usage_record}). The INSERT guard:
 * <pre>{@code
 *   INSERT INTO billing_charge (source_id, subscriber_id, units, rate, cost)
 *   SELECT ?, ?, ?, ?, ?
 *   WHERE NOT EXISTS (SELECT 1 FROM billing_charge WHERE source_id = ?)
 * }</pre>
 * returns 0 rows affected when a charge for that {@code source_id} already exists.
 * This is a silent no-op — no exception, no mutation of the existing row.
 *
 * <h3>Why {@code WHERE NOT EXISTS} instead of INSERT IGNORE</h3>
 * <p>{@code INSERT IGNORE} is MySQL-specific. The {@code WHERE NOT EXISTS} pattern is
 * portable across H2 ({@code MODE=MySQL}) and MySQL — required for unit tests against H2
 * and production deployment against MySQL.
 *
 * <h3>Frozen source</h3>
 * <p>This writer NEVER touches {@code usage_record}. The source table is read-only from
 * the job's perspective; idempotency is achieved by checking/writing {@code billing_charge}
 * only.
 *
 * @see BillingCharge
 */
public class BillingChargeWriter implements ItemWriter<BillingCharge> {

    private static final Logger log = LoggerFactory.getLogger(BillingChargeWriter.class);

    /**
     * Guarded INSERT: inserts a row only when no existing row has the same {@code source_id}.
     * Portable across H2 (MODE=MySQL) and MySQL.
     */
    private static final String GUARDED_INSERT_SQL =
            "INSERT INTO billing_charge (source_id, subscriber_id, units, rate, cost)"
                    + " SELECT ?, ?, ?, ?, ?"
                    + " WHERE NOT EXISTS (SELECT 1 FROM billing_charge WHERE source_id = ?)";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructs the writer.
     *
     * @param jdbcTemplate the JDBC template backed by the host's DataSource
     */
    public BillingChargeWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Writes each {@link BillingCharge} in the chunk using an idempotent guarded INSERT.
     *
     * <p>A charge whose {@code source_id} already exists in {@code billing_charge} is
     * silently skipped (0 rows affected). A charge being written for the first time
     * produces exactly 1 row.
     *
     * @param chunk the batch of billing charges to persist
     */
    @Override
    public void write(Chunk<? extends BillingCharge> chunk) {
        for (BillingCharge charge : chunk) {
            int updated = jdbcTemplate.update(
                    GUARDED_INSERT_SQL,
                    charge.sourceId(),
                    charge.subscriberId(),
                    charge.units(),
                    charge.rateMinor(),
                    charge.costMinor(),
                    charge.sourceId());   // WHERE NOT EXISTS lookup key

            if (updated == 0) {
                log.debug(
                        "billing_charge source_id={} already exists — skipping INSERT (no-op)",
                        charge.sourceId());
            } else {
                log.debug("billing_charge source_id={} inserted cost={}",
                        charge.sourceId(), charge.costMinor());
            }
        }
        log.info("Writer committed {} items", chunk.size());
    }
}
