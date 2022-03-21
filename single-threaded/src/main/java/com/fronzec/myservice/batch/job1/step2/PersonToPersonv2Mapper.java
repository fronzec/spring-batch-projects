package com.fronzec.myservice.batch.job1.step2;

import com.fronzec.myservice.person.PersonsEntity;
import com.fronzec.myservice.personv2.PersonsV2Entity;
import java.util.UUID;

public class PersonToPersonv2Mapper {

  private PersonToPersonv2Mapper() {
  }

  public static PersonsV2Entity fromTo(PersonsEntity entity) {
    PersonsV2Entity personsV2Entity = new PersonsV2Entity();
    personsV2Entity.setEmail(entity.getEmail());
    personsV2Entity.setFirstName(entity.getFirstName());
    personsV2Entity.setProfession(entity.getProfession());
    personsV2Entity.setUuidV4(UUID.randomUUID()
            .toString());
    return personsV2Entity;
  }
}
