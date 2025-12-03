/* 2025 */
package com.fronzec.frbatchservice.batchjobs.job1.step3;

import com.fronzec.frbatchservice.personv2.PersonsV2Entity;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.PagingQueryProvider;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Step3Reader {

    @Value("${fr-batch-service.jobs.job1.step3.reader.chunk-size:1000}")
    private int chunkSize;

    /**
     * Creates a JdbcPagingItemReader configured to page PersonsV2Entity rows filtered by fk_dispatched_group_id.
     *
     * @param  pagingQueryProvider      the paging query provider used to build the SQL for pagination
     * @param  entityPersonV2RowMapper  a RowMapper that converts result set rows into PersonsV2Entity instances
     * @return                          a configured JdbcPagingItemReader for PersonsV2Entity with page size from configuration,
     *                                  parameter `fk_dispatched_group_id` set to `null`, and state saving disabled
     * @throws Exception                if the reader cannot be constructed
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<PersonsV2Entity> itemReader(
            DataSource dataSource,
            PagingQueryProvider pagingQueryProvider,
            EntityPersonV2RowMapper entityPersonV2RowMapper)
            throws Exception {
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

    /**
     * Create an EntityPersonV2RowMapper used to map JDBC result set rows to PersonsV2Entity instances.
     *
     * @return a new EntityPersonV2RowMapper instance
     */
    @Bean
    public EntityPersonV2RowMapper entityPersonV2RowMapper() {
        return new EntityPersonV2RowMapper();
    }

    /**
     * Creates a SqlPagingQueryProviderFactoryBean configured to page over the persons_v2 table,
     * selecting common person columns, filtering by the `fk_dispatched_group_id` parameter,
     * and ordering results by `id`.
     *
     * @return a configured SqlPagingQueryProviderFactoryBean for paging queries against `persons_v2`
     */
    @Bean
    public SqlPagingQueryProviderFactoryBean pagingQueryProvider(DataSource dataSource) {
        SqlPagingQueryProviderFactoryBean provider = new SqlPagingQueryProviderFactoryBean();
        provider.setDataSource(dataSource);
        provider.setSelectClause(
                "select id, first_name, last_name, email, profession, salary, uuid_v4, created_at,"
                        + " updated_at");
        provider.setFromClause("from persons_v2");
        provider.setWhereClause("where fk_dispatched_group_id is :fk_dispatched_group_id");
        provider.setSortKey("id");
        return provider;
    }
}