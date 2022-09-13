package com.fronzec.myservice.person;


import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PersonRepository extends JpaRepository<PersonsEntity, Long> {

  List<PersonsEntity> findByIdGreaterThanAndProcessedIsFalseOrderByIdAsc(Long id, Pageable pageable);

  @Modifying
  @Query(value = "update persons set processed = true, updated_at = CURRENT_TIMESTAMP where id in (:ids)", nativeQuery = true)
  int updateProcessedInIds(List<Long> ids);

}
