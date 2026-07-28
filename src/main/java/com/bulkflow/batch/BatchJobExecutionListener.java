package com.bulkflow.batch;

import com.bulkflow.observability.BatchMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.time.Duration;

public class BatchJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(BatchJobExecutionListener.class);

    private final BatchMetricsService metricsService;

    public BatchJobExecutionListener(BatchMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String batchId = jobExecution.getJobParameters().getString("batchId");
        String feedType = jobExecution.getJobParameters().getString("feedType");
        String sourceFile = jobExecution.getJobParameters().getString("sourceFile");
        log.info("JOB_START batchId={} feedType={} source={}", batchId, feedType, sourceFile);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String batchId = jobExecution.getJobParameters().getString("batchId");
        String feedType = jobExecution.getJobParameters().getString("feedType");
        String sourceFile = jobExecution.getJobParameters().getString("sourceFile");

        long readCount = jobExecution.getStepExecutions().stream()
                .mapToLong(s -> s.getReadCount()).sum();
        long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(s -> s.getWriteCount()).sum();
        long skipCount = jobExecution.getStepExecutions().stream()
                .mapToLong(s -> s.getSkipCount()).sum();

        long durationMs = 0;
        if (jobExecution.getEndTime() != null && jobExecution.getStartTime() != null) {
            durationMs = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        }

        log.info("JOB_END batchId={} feedType={} status={} read={} wrote={} skipped={} durationMs={}",
                batchId, feedType, jobExecution.getStatus(), readCount, writeCount, skipCount, durationMs);

        metricsService.recordBatchComplete(batchId, feedType, sourceFile != null ? sourceFile : "",
                readCount, writeCount, skipCount, durationMs, jobExecution.getStatus().toString());
    }
}
