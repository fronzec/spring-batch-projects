/* 2026 */
package com.fronzec.frbatchservice.batchjobs.job2;

import com.fronzec.frbatchservice.batchjobs.dispatchedgroups.DispatchedGroupEntity;
import com.fronzec.frbatchservice.batchjobs.job1.step3.PayloadItemInfo;
import java.util.List;

/**
 * Wrapper carrying the source {@link DispatchedGroupEntity} together with the list of {@link
 * PayloadItemInfo} built from its associated {@code PersonsV2Entity} rows.
 *
 * <p>Used as the output type of {@link Job2Processor} and input type of {@link Job2Writer} so the
 * writer has direct access to the entity it must update.
 */
public record RecoveryGroupPayload(
    DispatchedGroupEntity dispatchedGroup, List<PayloadItemInfo> payloadItems) {}
