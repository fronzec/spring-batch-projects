package com.fronzec.singlethreaded.personv2;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PersonV2Repository extends JpaRepository<PersonsV2Entity, Long> {
  @Modifying
  @Query(
    value = "update persons_v2 set fk_dispatched_group_id = :dispatchedGroupId, updated_at = CURRENT_TIMESTAMP where id in (:ids)",
    nativeQuery = true
  )
  int updateDispatchedGroupId(Long dispatchedGroupId, List<Long> ids);
}
