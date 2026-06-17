package com.fronzec.plugins.partitionedharvester.batch;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hand-rolled id-range partitioner for the {@code usage_record} table.
 *
 * <p>Determines partition boundaries using only:
 * {@code SELECT MIN(id), MAX(id) FROM usage_record} — a single cheap index-only
 * scan. No full-table scan or row sampling occurs at planning time.
 *
 * <h3>Algorithm</h3>
 * <p>Given MIN, MAX, and gridSize:
 * <pre>
 *   rangeSize = (MAX - MIN) / gridSize    // integer division (floor)
 *   range[i].min = MIN + i * rangeSize
 *   range[i].max = MIN + (i+1) * rangeSize - 1
 *   range[gridSize-1].max = MAX           // last partition absorbs the remainder
 * </pre>
 * <p>Ranges are equal-width with the last partition absorbing any remainder from
 * integer division, ensuring the union is exactly {@code [MIN, MAX]}.
 *
 * <h3>Edge cases</h3>
 * <ul>
 *   <li><strong>Empty table</strong> (MIN/MAX null): returns a single
 *       {@code ExecutionContext{minId=1, maxId=0}} so the worker SQL
 *       ({@code WHERE id BETWEEN 1 AND 0}) reads 0 rows and completes normally.</li>
 *   <li><strong>Single row</strong> (MIN=MAX): ranges converge around that single id;
 *       partitions with equal minId and maxId are valid no-ops for all but one.</li>
 *   <li><strong>gridSize > distinct id spread</strong>: ranges may be very narrow
 *       (width 0 or 1), producing empty partitions that are harmless.</li>
 * </ul>
 *
 * <h3>gridSize residual note (WU-05)</h3>
 * <p>The {@code gridSize} parameter to the constructor is fixed at build time inside
 * {@code configureJob} (PR-2, WU-10) because {@code configureJob} receives no
 * {@code JobParameters}. The {@code partition(int)} override signature means Spring Batch
 * will call {@code partition(gridSize)} at step launch, but this implementation ignores
 * the argument and uses the constructor-injected value. The {@code GRID_SIZE} JobParameter
 * is validated by {@code PartitionedHarvesterJobParametersValidator} to equal the build-time
 * default (4). Full runtime-dynamic gridSize resolution is deferred to a future enhancement.
 *
 * @see org.springframework.batch.core.partition.Partitioner
 */
public class IdRangePartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(IdRangePartitioner.class);

    private static final String MIN_MAX_SQL = "SELECT MIN(id), MAX(id) FROM usage_record";

    private final JdbcTemplate jdbc;

    /**
     * Fixed gridSize baked in at construction time (see class Javadoc for the
     * build-time constraint rationale).
     */
    private final int gridSize;

    /**
     * Constructs the partitioner.
     *
     * @param jdbc     JdbcTemplate to query MIN/MAX from {@code usage_record}
     * @param gridSize number of partitions; fixed at build time in {@code configureJob}
     */
    public IdRangePartitioner(JdbcTemplate jdbc, int gridSize) {
        this.jdbc = jdbc;
        this.gridSize = gridSize;
    }

    /**
     * Produces partition {@link ExecutionContext}s for the manager step.
     *
     * <p>The {@code gridSize} argument from Spring Batch is ignored; the constructor-injected
     * value is used instead (see class Javadoc).
     *
     * @param gridSize hint from Spring Batch's PartitionStep (ignored; constructor value used)
     * @return map of partition name → ExecutionContext with {@code minId}, {@code maxId},
     *         {@code partitionName}
     */
    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        long[] minMax = queryMinMax();
        long min = minMax[0];
        long max = minMax[1];

        // Empty table: return a single no-op partition so the worker reads 0 rows
        if (min > max) {
            log.info("usage_record is empty — returning single no-op partition");
            Map<String, ExecutionContext> result = new HashMap<>(1);
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong("minId", 1L);
            ctx.putLong("maxId", 0L);
            ctx.putString("partitionName", "partition0");
            result.put("partition0", ctx);
            return result;
        }

        long span = max - min;
        long rangeSize = span / this.gridSize;

        log.info("Partitioning usage_record: MIN={} MAX={} span={} gridSize={} rangeSize={}",
                min, max, span, this.gridSize, rangeSize);

        Map<String, ExecutionContext> partitions = new HashMap<>(this.gridSize);
        for (int i = 0; i < this.gridSize; i++) {
            long rangeMin = min + (long) i * rangeSize;
            long rangeMax = (i == this.gridSize - 1) ? max : (min + (long) (i + 1) * rangeSize - 1);

            String name = "partition" + i;
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong("minId", rangeMin);
            ctx.putLong("maxId", rangeMax);
            ctx.putString("partitionName", name);
            partitions.put(name, ctx);

            log.debug("  {} → [{}, {}]", name, rangeMin, rangeMax);
        }

        return partitions;
    }

    /**
     * Queries MIN(id) and MAX(id) from {@code usage_record}.
     *
     * @return two-element array {@code [min, max]}; if the table is empty returns
     *         {@code [1, 0]} (min > max signals the empty case to {@link #partition(int)})
     */
    private long[] queryMinMax() {
        return jdbc.queryForObject(MIN_MAX_SQL, (rs, rowNum) -> {
            Object minObj = rs.getObject(1);
            Object maxObj = rs.getObject(2);
            if (minObj == null || maxObj == null) {
                // Empty table: signal with min > max
                return new long[]{1L, 0L};
            }
            return new long[]{((Number) minObj).longValue(), ((Number) maxObj).longValue()};
        });
    }
}
