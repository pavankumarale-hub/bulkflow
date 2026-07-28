package com.bulkflow.batch;

import com.bulkflow.model.AccountRecord;
import com.bulkflow.model.TransactionRecord;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.json.JacksonJsonObjectReader;
import org.springframework.batch.item.json.JsonItemReader;
import org.springframework.batch.item.json.builder.JsonItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

/**
 * Step-scoped readers so the file path from JobParameters is resolved at step
 * execution time, not at application startup.
 */
@Configuration
public class ReaderConfig {

    @Bean
    @StepScope
    public FlatFileItemReader<AccountRecord> accountCsvReader(
            @Value("#{jobParameters['localPath']}") String localPath) {
        return new FlatFileItemReaderBuilder<AccountRecord>()
                .name("accountCsvReader")
                .resource(new FileSystemResource(localPath))
                .linesToSkip(1) // header row
                .delimited()
                .names("accountId", "email", "firstName", "lastName", "status",
                        "dateOfBirth", "phone", "creditLimit", "currency")
                .fieldSetMapper(new AccountFieldSetMapper())
                .build();
    }

    @Bean
    @StepScope
    public JsonItemReader<TransactionRecord> transactionJsonReader(
            @Value("#{jobParameters['localPath']}") String localPath) {
        return new JsonItemReaderBuilder<TransactionRecord>()
                .name("transactionJsonReader")
                .resource(new FileSystemResource(localPath))
                .jsonObjectReader(new JacksonJsonObjectReader<>(TransactionRecord.class))
                .build();
    }
}
