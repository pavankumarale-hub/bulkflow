package com.bulkflow.batch;

import com.bulkflow.config.BulkFlowProperties;
import com.bulkflow.model.AccountRecord;
import com.bulkflow.observability.BatchMetricsService;
import com.bulkflow.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class AccountJobConfig {

    private static final Logger log = LoggerFactory.getLogger(AccountJobConfig.class);

    private final BulkFlowProperties props;
    private final BatchMetricsService metricsService;

    public AccountJobConfig(BulkFlowProperties props, BatchMetricsService metricsService) {
        this.props = props;
        this.metricsService = metricsService;
    }

    @Bean
    public Job accountBatchJob(JobRepository jobRepository, Step accountIngestStep) {
        return new JobBuilder("accountBatchJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(accountIngestStep)
                .listener(new BatchJobExecutionListener(metricsService))
                .build();
    }

    @Bean
    public Step accountIngestStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            FlatFileItemReader<AccountRecord> accountCsvReader,
            AccountItemProcessor processor,
            SkipRecordListener<AccountRecord> skipListener,
            DataSource dataSource) {

        int chunkSize = props.getBatch().getChunkSize();
        JdbcBatchItemWriter<AccountRecord> writer = accountWriter(dataSource);

        return new StepBuilder("accountIngestStep", jobRepository)
                .<AccountRecord, AccountRecord>chunk(chunkSize, txManager)
                .reader(accountCsvReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(ValidationException.class)
                .skip(FlatFileParseException.class)
                .skipLimit(props.getBatch().getSkipLimit())
                .retry(TransientDataAccessException.class)
                .retryLimit(props.getBatch().getRetryLimit())
                .listener(skipListener)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<AccountRecord> accountWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<AccountRecord>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO accounts
                          (account_id, email, first_name, last_name, status, date_of_birth,
                           phone, credit_limit, currency, row_hash, batch_id, feed_type)
                        VALUES
                          (:accountId, :email, :firstName, :lastName, :status, :dateOfBirth,
                           :phone, :creditLimit, :currency, :rowHash, :batchId, 'accounts')
                        ON CONFLICT (row_hash) DO NOTHING
                        """)
                .beanMapped()
                .build();
    }
}
