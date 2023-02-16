package com.fronzec.singlethreaded.group;

import io.hypersistence.utils.spring.repository.BaseJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupedRepository extends BaseJpaRepository<GroupedEntity, Long> {

}
