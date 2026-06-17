/**
 * Root package for the partitioned-harvester job plugin.
 *
 * <p>This plugin implements id-range partitioned billing over a frozen {@code usage_record}
 * table. Each {@code usage_record} row produces exactly one {@code billing_charge} row
 * (cost = units × rate). The headline capability is partitioned restart with no double-billing.
 *
 * <p>Implemented in PR-1 (scaffold), PR-2 (wiring), PR-3 (integration tests).
 */
package com.fronzec.plugins.partitionedharvester;
