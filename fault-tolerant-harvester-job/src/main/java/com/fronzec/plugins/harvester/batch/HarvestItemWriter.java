package com.fronzec.plugins.harvester.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Marks each successfully processed {@link HarvestRow} as done in {@code harvest_source}.
 *
 * <h3>Idempotency</h3>
 * <p>The UPDATE predicate includes {@code AND processed = FALSE}, so a row already marked
 * {@code TRUE} (e.g., from a prior completed chunk on restart) causes 0 rows updated — a
 * safe no-op. No exception is thrown on 0-row updates.
 *
 * <h3>Restart safety</h3>
 * <p>Combined with {@code JdbcCursorItemReader.setSaveState(true)}, the reader fast-forwards
 * past already-committed rows on restart using the saved {@code currentItemCount} from
 * {@code BATCH_STEP_EXECUTION_CONTEXT}. The idempotent writer is a belt-and-suspenders
 * guard for the rare case where a row is re-delivered at a chunk boundary.
 */
public class HarvestItemWriter implements ItemWriter<HarvestRow> {

    private static final Logger log = LoggerFactory.getLogger(HarvestItemWriter.class);

    private static final String UPDATE_SQL =
            "UPDATE harvest_source SET processed = TRUE WHERE id = ? AND processed = FALSE";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructs the writer.
     *
     * @param jdbcTemplate the JDBC template backed by the host's DataSource
     */
    public HarvestItemWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Marks each item in the chunk as processed.
     *
     * @param chunk the successfully processed items to write
     */
    @Override
    public void write(Chunk<? extends HarvestRow> chunk) {
        for (HarvestRow row : chunk) {
            int updated = jdbcTemplate.update(UPDATE_SQL, row.id());
            if (updated == 0) {
                // Row was already processed (restart re-delivery or duplicate). Safe no-op.
                log.debug("harvest_source.id={} already processed — skipping UPDATE (no-op)", row.id());
            } else {
                log.debug("harvest_source.id={} marked processed=TRUE", row.id());
            }
        }
        log.info("Writer committed {} items", chunk.size());
    }
}
