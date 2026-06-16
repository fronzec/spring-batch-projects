/**
 * Root package for the fault-tolerant harvester job plugin.
 *
 * <p>This plugin implements a fault-tolerant Spring Batch chunk step that demonstrates:
 * skip-to-dead-letter, retry with exponential backoff, and restartability.
 *
 * <p>Entry point: {@link com.fronzec.plugins.harvester.FaultTolerantHarvesterJobPlugin}.
 */
package com.fronzec.plugins.harvester;
