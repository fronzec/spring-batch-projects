/* 2024-2025 */
package com.fronzec.frbatchservice.batchjobs.dispatchedgroups;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchedGroupEntityRepository
    extends JpaRepository<DispatchedGroupEntity, Long> {

  List<DispatchedGroupEntity> findByDispatchStatus(DispatchStatus dispatchStatus);
}
