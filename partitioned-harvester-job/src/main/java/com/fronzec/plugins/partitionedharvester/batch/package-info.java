/**
 * Batch components for the partitioned-harvester job plugin.
 *
 * <p>Contains the partitioner, reader, processor, writer, domain types, row mapper,
 * and validator that together form the partitioned chunk step pipeline.
 *
 * <p>Implemented in PR-1 (domain types, partitioner) and PR-2 (reader, processor, writer,
 * validator, plugin wiring).
 */
package com.fronzec.plugins.partitionedharvester.batch;
