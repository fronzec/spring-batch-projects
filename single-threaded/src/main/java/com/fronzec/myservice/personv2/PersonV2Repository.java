package com.fronzec.myservice.personv2;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonV2Repository extends JpaRepository<PersonsV2Entity, Long> {}
