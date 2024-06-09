package com.fronzec.singlethreaded.batchjobs.dispatchedgroups;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DispatchedGroupEntityRepository
    extends JpaRepository<DispatchedGroupEntity, Long> {}
