package com.fronzec.singlethreaded.group;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class GroupedRowMapper implements RowMapper<GroupedEntity> {

    @Override
    public GroupedEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        GroupedEntity entity = new GroupedEntity();
        entity.setDispatchStatus(GroupedDispatchStatuses.PENDING);
        entity.setProfession(rs.getString("profession"));
        entity.setSnapshotDate(LocalDate.parse(rs.getString("snapshot_date")));
        entity.setTotalSalary(rs.getBigDecimal("total_salary"));
        return entity;
    }
}