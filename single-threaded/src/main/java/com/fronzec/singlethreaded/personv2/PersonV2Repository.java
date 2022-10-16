package com.fronzec.singlethreaded.personv2;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonV2Repository extends JpaRepository<PersonsV2Entity, Long> {}
