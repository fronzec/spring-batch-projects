/* 2025 */
package com.fronzec.frbatchservice.batchjobs.job1.step1;

import com.fronzec.frbatchservice.batchjobs.job1.Person;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CsvWriter {

    @StepScope
    @Bean
    public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql(
                        "INSERT INTO persons (first_name, last_name, email, profession) VALUES"
                                + " (:firstName, :lastName, :email, :profession)")
                .dataSource(dataSource)
                .build();
    }
}
