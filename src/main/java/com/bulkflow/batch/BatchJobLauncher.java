package com.bulkflow.batch;

import com.bulkflow.ingestion.FeedType;
import com.bulkflow.observability.BatchMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class BatchJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(BatchJobLauncher.class);

    private final JobLauncher jobLauncher;
    private final Job accountBatchJob;
    private final Job transactionBatchJob;
    private final BatchMetricsService metricsService;

    public BatchJobLauncher(JobLauncher jobLauncher,
                            Job accountBatchJob,
                            Job transactionBatchJob,
                            BatchMetricsService metricsService) {
        this.jobLauncher = jobLauncher;
        this.accountBatchJob = accountBatchJob;
        this.transactionBatchJob = transactionBatchJob;
        this.metricsService = metricsService;
    }

    public String launch(String objectKey, String localPath, FeedType feedType) {
        String batchId = "batch-" + UUID.randomUUID();
        MDC.put("batch_id", batchId);
        MDC.put("feed_type", feedType.getValue());
        try {
            log.info("Launching batch: batchId={} feedType={} source={}", batchId, feedType, objectKey);
            metricsService.recordBatchStart(batchId, feedType.getValue(), objectKey);

            JobParameters params = new JobParametersBuilder()
                    .addString("batchId", batchId)
                    .addString("feedType", feedType.getValue())
                    .addString("sourceFile", objectKey)
                    .addString("localPath", localPath)
                    .addLong("timestamp", Instant.now().toEpochMilli())
                    .toJobParameters();

            Job job = (feedType == FeedType.ACCOUNTS) ? accountBatchJob : transactionBatchJob;
            jobLauncher.run(job, params);
            return batchId;

        } catch (Exception e) {
            log.error("Batch launch failed: batchId={}", batchId, e);
            metricsService.recordBatchFailure(batchId);
            throw new RuntimeException("Batch launch failed for " + objectKey, e);
        } finally {
            MDC.clear();
        }
    }
}
