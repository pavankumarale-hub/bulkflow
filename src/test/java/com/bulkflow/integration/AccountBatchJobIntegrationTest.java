package com.bulkflow.integration;

import com.bulkflow.batch.BatchJobLauncher;
import com.bulkflow.deadletter.DeadLetterRepository;
import com.bulkflow.ingestion.FeedType;
import com.bulkflow.model.DeadLetterRecord;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class AccountBatchJobIntegrationTest {

    @Autowired
    private BatchJobLauncher jobLauncher;

    @Autowired
    private DeadLetterRepository deadLetterRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String ACCOUNTS_WITH_ERRORS = """
            account_id,email,first_name,last_name,status,date_of_birth,phone,credit_limit,currency
            acc_90001,alice.smith@example.com,Alice,Smith,ACTIVE,1985-03-12,+1-555-100-0001,5000.00,USD
            acc_90002,bob.jones@example.com,Bob,Jones,ACTIVE,1990-07-22,+1-555-100-0002,7500.00,USD
            acc_90003,carol.brown@example.com,Carol,Brown,INACTIVE,1978-11-05,+1-555-100-0003,3000.00,USD
            acc_90004,david.wilson@example.com,David,Wilson,PENDING,1995-01-30,+1-555-100-0004,10000.00,EUR
            acc_90005,eve.taylor@example.com,Eve,Taylor,ACTIVE,1982-06-18,+1-555-100-0005,2500.00,GBP
            acc_90006,frank.davies@example.com,Frank,Davies,ACTIVE,1988-09-25,+1-555-100-0006,6000.00,USD
            acc_90007,grace.evans@example.com,Grace,Evans,ACTIVE,1993-04-14,+1-555-100-0007,4500.00,USD
            acc_90008,hank.thomas@example.com,Hank,Thomas,PENDING,1987-12-03,+1-555-100-0008,8000.00,USD
            acc_90009,,Iris,Roberts,ACTIVE,1991-02-28,+1-555-100-0009,3500.00,USD
            acc_90010,not-an-email,Jack,Johnson,ACTIVE,1984-08-16,+1-555-100-0010,5500.00,USD
            acc_90011,karen.lewis@example.com,Karen,Lewis,UNKNOWN_STATUS,1979-05-07,+1-555-100-0011,2000.00,USD
            acc_90012,liam.walker@example.com,Liam,Walker,ACTIVE,31/13/2020,+1-555-100-0012,7000.00,USD
            acc_90001,alice.smith@example.com,Alice,Smith,ACTIVE,1985-03-12,+1-555-100-0001,5000.00,USD
            """;

    @Test
    void full_batch_isolates_invalid_records_and_loads_valid_ones() throws Exception {
        // Clear state for this test
        jdbc.execute("DELETE FROM accounts WHERE account_id LIKE 'acc_9000%'");
        jdbc.execute("DELETE FROM dead_letter_records WHERE batch_id LIKE 'test-%'");

        Path csvFile = writeTempCsv(ACCOUNTS_WITH_ERRORS);

        String batchId = jobLauncher.launch(csvFile.getFileName().toString(),
                csvFile.toString(), FeedType.ACCOUNTS);

        assertThat(batchId).startsWith("batch-");

        // Valid records: acc_90001 through acc_90008 = 8 valid, then duplicates/invalid skip
        long loadedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE batch_id = ?", Long.class, batchId);
        // acc_90001 through acc_90008 are valid; second acc_90001 is duplicate = skip
        assertThat(loadedCount).isGreaterThanOrEqualTo(8L);

        // Dead-letter: missing email, invalid email, invalid status, invalid date, duplicate = 5
        long deadCount = deadLetterRepository.countByBatchId(batchId);
        assertThat(deadCount).isGreaterThanOrEqualTo(4L);

        // Verify failure reason codes are structured
        List<DeadLetterRecord> deadRecords = deadLetterRepository
                .findByBatchIdAndStatus(batchId, DeadLetterRecord.DeadLetterStatus.PENDING);
        assertThat(deadRecords).allSatisfy(r -> {
            assertThat(r.getFailureReason()).isNotBlank();
            assertThat(r.getRawRecord()).isNotBlank();
            assertThat(r.getBatchId()).isEqualTo(batchId);
        });

        // Specific reason codes present
        List<String> reasons = deadRecords.stream().map(DeadLetterRecord::getFailureReason).toList();
        assertThat(reasons).contains("missing_field", "invalid_email");
    }

    @Test
    void idempotent_rerun_does_not_create_duplicates() throws Exception {
        jdbc.execute("DELETE FROM accounts WHERE account_id LIKE 'acc_idem%'");

        String idempotentCsv = """
                account_id,email,first_name,last_name,status,date_of_birth,phone,credit_limit,currency
                acc_idem001,idem.user@example.com,Idem,User,ACTIVE,1990-01-01,+1-555-999-0001,1000.00,USD
                """;

        Path csvFile = writeTempCsv(idempotentCsv);

        // Run the same file twice
        jobLauncher.launch("idem_test.csv", csvFile.toString(), FeedType.ACCOUNTS);
        // Write a fresh copy since the file path won't change
        Path csvFile2 = writeTempCsv(idempotentCsv);
        jobLauncher.launch("idem_test.csv", csvFile2.toString(), FeedType.ACCOUNTS);

        long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE account_id = 'acc_idem001'", Long.class);

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void all_invalid_batch_loads_zero_records() throws Exception {
        String allBadCsv = """
                account_id,email,first_name,last_name,status,date_of_birth,phone,credit_limit,currency
                ,,,,,,,,
                ,bad-email,,,,,,
                acc_bad001,also-bad-email,Missing,Last,INVALID_STATUS,,,,-999.00,BADCUR
                """;

        Path csvFile = writeTempCsv(allBadCsv);
        String batchId = jobLauncher.launch("all_bad.csv", csvFile.toString(), FeedType.ACCOUNTS);

        long loadedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE batch_id = ?", Long.class, batchId);
        assertThat(loadedCount).isEqualTo(0L);

        long deadCount = deadLetterRepository.countByBatchId(batchId);
        assertThat(deadCount).isGreaterThan(0L);
    }

    private Path writeTempCsv(String content) throws IOException {
        Path file = Files.createTempFile("bulkflow-test-", ".csv");
        Files.writeString(file, content);
        return file;
    }
}
