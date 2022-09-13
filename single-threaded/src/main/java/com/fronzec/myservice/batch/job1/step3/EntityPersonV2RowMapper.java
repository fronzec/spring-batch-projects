package com.fronzec.myservice.batch.job1.step3;

import com.fronzec.myservice.personv2.PersonsV2Entity;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;

public class EntityPersonV2RowMapper implements RowMapper<PersonsV2Entity> {

  @Override
  public PersonsV2Entity mapRow(ResultSet rs, int rowNum) throws SQLException {
    PersonsV2Entity personsV2 = new PersonsV2Entity();
    personsV2.setId(rs.getLong("id"));
    personsV2.setEmail(rs.getString("email"));
    personsV2.setFirstName(rs.getString("first_name"));
    personsV2.setLastName(rs.getString("last_name"));
    personsV2.setProfession(rs.getString("profession"));
    personsV2.setSalary(rs.getBigDecimal("salary"));
    personsV2.setUuidV4(rs.getString("uuid_v4"));
    personsV2.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
    personsV2.setCreatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
    return personsV2;
  }
}
