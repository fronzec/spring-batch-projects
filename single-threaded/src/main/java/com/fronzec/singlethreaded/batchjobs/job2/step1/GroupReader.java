package com.fronzec.singlethreaded.batchjobs.job2.step1;

import com.fronzec.singlethreaded.group.GroupedEntity;
import com.fronzec.singlethreaded.group.GroupedRowMapper;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component("job2-GroupItemReader")
@StepScope
public class GroupReader extends JdbcCursorItemReader<GroupedEntity> {
    String query = """
SELECT
  snapshot_date,
  profession,
  sum(salary) as total_salary
from
  persons_v2
where
  snapshot_date = ':snapshot_date'
GROUP by
  profession;
    """;

    public GroupReader(DataSource datasource,
                       @Value("#{jobParameters[DATE]}") String processingDate
                       ) throws Exception {
        this.setName("job2-GroupItemReader");
        this.setDataSource(datasource);
        // TODO build the query
        String builtQuery = query.replaceAll(":snapshot_date",processingDate);
        this.setSql(builtQuery);
        this.setRowMapper(new GroupedRowMapper());
        this.setSaveState(false);
        this.afterPropertiesSet();
    }

}
