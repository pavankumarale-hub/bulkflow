package com.bulkflow.batch;

import com.bulkflow.config.BulkFlowProperties;
import com.bulkflow.model.TransactionRecord;
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
import org.springframework.batch.item.json.JsonItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class TransactionJobConfig {

    private static final Logger log = LoggerFactory.getLogger(TransactionJobConfig.class);

    private final BulkFlowProperties props;
    private final BatchMetricsService metricsService;

    public TransactionJobConfig(BulkFlowProperties props, BatchMetricsService metricsService) {
        this.props = props;
        this.metricsService = metricsService;
    }

    @Bean
    public Job transactionBatchJob(JobRepository jobRepository, Step transactionIngestStep) {
        return new JobBuilder("transactionBatchJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(transactionIngestStep)
                .listener(new BatchJobExecutionListener(metricsService))
                .build();
    }

    @Bean
    public Step transactionIngestStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            JsonItemReader<TransactionRecord> transactionJsonReader,
            TransactionItemProcessor processor,
            SkipRecordListener<TransactionRecord> skipListener,
            DataSource dataSource) {

        int chunkSize = props.getBatch().getChunkSize();
        JdbcBatchItemWriter<TransactionRecord> writer = transactionWriter(dataSource);

        return new StepBuilder("transactionIngestStep", jobRepository)
                .<TransactionRecord, TransactionRecord>chunk(chunkSize, txManager)
                .reader(transactionJsonReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(ValidationException.class)
                .skipLimit(props.getBatch().getSkipLimit())
                .retry(TransientDataAccessException.class)
                .retryLimit(props.getBatch().getRetryLimit())
                .listener(skipListener)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<TransactionRecord> transactionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TransactionRecord>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO transactions
                          (transaction_id, account_id, amount, currency, transaction_type,
                           transaction_date, description, row_hash, batch_id, feed_type)
                        VALUES
                          (:transactionId, :accountId, :amount, :currency, :transactionType,
                           :transactionDate, :description, :rowHash, :batchId, 'transactions')
                        ON CONFLICT (row_hash) DO NOTHING
                        """)
                .beanMapped()
                .build();
    }
}
