package com.fronzec.myservice.batch;


import com.fronzec.myservice.batch.persons.Customer;
import com.fronzec.myservice.batch.persons.CustomerOutput;
import com.fronzec.myservice.batch.persons.Person;
import com.fronzec.myservice.batch.persons.PersonItemProcessor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import javax.sql.DataSource;


@Configuration
@EnableBatchProcessing // Only one class needs to be configured with batch processing
public class BatchConfiguration {

    /**
     * JobBuilderFactory for JobBuilder which sets the JobRepository automatically
     */
    public final JobBuilderFactory jobBuilderFactory;

    /**
     *  Convenient factory for a StepBuilder which sets the JobRepository automatically
     */
    public final StepBuilderFactory stepBuilderFactory;

    public final JobRepository jobRepository;

    public BatchConfiguration(JobBuilderFactory jobBuilderFactory, StepBuilderFactory stepBuilderFactory, JobRepository jobRepository) {
        this.jobBuilderFactory = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.jobRepository = jobRepository;
    }


    @Bean
    public FlatFileItemReader<Person> reader() {
        return new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader")
                .resource(new ClassPathResource("sample-data-1k.csv"))
                .delimited()
                .names(new String[]{"firstName", "lastName"})
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Person>() {{
                    setTargetType(Person.class);
                }})
                .build();
    }

    @Bean
    public FlatFileItemReader<Customer> readerCustomer() {
        return new FlatFileItemReaderBuilder<Customer>()
                .name("customerItemReader")
                .resource(new ClassPathResource("sample-customers-1k.csv"))
                .delimited()
                .names("firstName", "lastName","email","profession")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<Customer>() {{
                    setTargetType(Customer.class);
                }})
                .build();
    }

    @Bean
    public PersonItemProcessor processor() {
        return new PersonItemProcessor();
    }

    @Bean
    public CustomerItemProcessor customerItemProcessor(){
        return new CustomerItemProcessor();
    }

    @Bean
    public JdbcBatchItemWriter<Person> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO persons (first_name, last_name) VALUES (:firstName, :lastName)")
                .dataSource(dataSource)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<CustomerOutput> writerCustomer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<CustomerOutput>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO customer (firstName, lastName, fullName, email, profession) VALUES (:firstName, :lastName, :fullName, :email, :profession)")
                .dataSource(dataSource)
                .build();
    }

    /**
     * Job configuration for import user info from csv to database
     * @param listener our job completion listener
     * @param step1 out step to import see {@link #step1(JdbcBatchItemWriter, MyChunkListener)}
     * @return the Job
     */
    @Bean(name = "myImportUserJob")
    public Job importUserJob(JobCompletionNotificationListener listener, Step step1) {

        return jobBuilderFactory.get("myImportUserJob")// JobName: The jobname could be different from bean name but is common to have the same value
                .incrementer(new RunIdIncrementer())// Job id increment identifier
                .listener(listener) // Job listeners that allow tracking of job lifecycle events
                .flow(step1) // Configure the first step for this job
                .end() // No more steps for our job, ready to build
                .build();
    }

    @Bean(name = "importCustomerJob")
    public Job importCustomerJob(JobCompletionNotificationListener listener, Step stepCustomer2) {
        return jobBuilderFactory.get("importCustomerJob")// JobName: The jobname could be different from bean name but is common to have the same value
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(stepCustomer2)
                .end().build();
    }

    /**
     * Step 1 for our job {@link #importUserJob(JobCompletionNotificationListener, Step)}
     * @param writer Job batch item writer
     * @return the step we are confuguring
     */
    @Bean
    public Step step1(JdbcBatchItemWriter<Person> writer, MyChunkListener myChunkListener) {
        return stepBuilderFactory.get("step1")// the bean name that allow creates our step
                .<Person, Person> chunk(10) //chunk size of items to process in each step
                .reader(reader()) // Bean reader configured
                .processor(processor()) // Bean processor configured
                .writer(writer) // Bean writer configured
                .listener(myChunkListener) // Chunk listener configured
                .build();
    }

    @Bean
    public Step stepCustomer2(JdbcBatchItemWriter<CustomerOutput> writer, MyChunkListener myChunkListener) {
        return stepBuilderFactory.get("stepCustomer2")// the bean name that allow creates our step
                .<Customer, CustomerOutput> chunk(20) //chunk size of items to process in each step
                .reader(readerCustomer()) // Bean reader configured
                .processor(customerItemProcessor()) // Bean processor configured
                .writer(writer) // Bean writer configured
                .listener(myChunkListener) // Chunk listener configured
                .build();
    }

    /**
     * If we need allow launch a job from an HTTP request we need to launch async,
     * To launch a Job we need the job and a JobLauncher
     * @return
     * @throws Exception
     */
    @Bean
    public JobLauncher asyncJobLauncher() throws Exception {
        var jobLauncher = new SimpleJobLauncher();
        jobLauncher.setJobRepository(jobRepository);
        jobLauncher.setTaskExecutor(new SimpleAsyncTaskExecutor("asyncJobExecutor"));// Job exexutor
        jobLauncher.afterPropertiesSet();
        return jobLauncher;
    }
}
