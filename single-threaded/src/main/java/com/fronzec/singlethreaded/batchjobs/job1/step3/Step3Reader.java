package com.fronzec.singlethreaded.batchjobs.job1.step3;

import com.fronzec.singlethreaded.personv2.PersonsV2Entity;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Step3Reader {

  @Value("${single-threaded.jobs.job1.step3.reader.chunk-size:1000}")
  private int chunkSize;

  @Bean
  @StepScope
  public JdbcPagingItemReader<PersonsV2Entity> itemReader(
    DataSource dataSource,
    PagingQueryProvider pagingQueryProvider,
    EntityPersonV2RowMapper entityPersonV2RowMapper
  ) {
    Map<String, Object> parameterValues = new HashMap<>();
    parameterValues.put("fk_dispatched_group_id", null);

    return new JdbcPagingItemReaderBuilder<PersonsV2Entity>()
      .name("step3Reader")
      .dataSource(dataSource)
      .queryProvider(pagingQueryProvider)
      .parameterValues(parameterValues)
      .pageSize(chunkSize)
      .rowMapper(entityPersonV2RowMapper)
      .saveState(false)
      .build();
  }

  @Bean
  public EntityPersonV2RowMapper entityPersonV2RowMapper() {
    return new EntityPersonV2RowMapper();
  }

  @Bean
  public SqlPagingQueryProviderFactoryBean pagingQueryProvider(DataSource dataSource) {
    SqlPagingQueryProviderFactoryBean provider = new SqlPagingQueryProviderFactoryBean();
    provider.setDataSource(dataSource);
    provider.setSelectClause(
      "select id, first_name, last_name, email, profession, salary, uuid_v4, created_at, updated_at"
    );
    provider.setFromClause("from persons_v2");
    provider.setWhereClause("where fk_dispatched_group_id is :fk_dispatched_group_id");
    provider.setSortKey("id");
    return provider;
  }
}
