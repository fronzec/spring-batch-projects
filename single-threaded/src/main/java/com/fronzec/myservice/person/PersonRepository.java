package com.fronzec.myservice.person;


import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<PersonsEntity, Long> {

  List<PersonsEntity> findByIdGreaterThanAndProcessedIsFalseOrderByIdAsc(Long id, Pageable pageable);

}
