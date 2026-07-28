package com.bulkflow.observability;

import com.bulkflow.deadletter.DeadLetterService;
import com.bulkflow.model.BatchRunMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class BatchMetricsService {

    private static final Logger log = LoggerFactory.getLogger(BatchMetricsService.class);

    private final BatchRunMetadataRepository metadataRepository;
    private final DeadLetterService deadLetterService;

    public BatchMetricsService(BatchRunMetadataRepository metadataRepository,
                               DeadLetterService deadLetterService) {
        this.metadataRepository = metadataRepository;
        this.deadLetterService = deadLetterService;
    }

    @Transactional
    public void recordBatchStart(String batchId, String feedType, String sourceFile) {
        BatchRunMetadata metadata = BatchRunMetadata.builder()
                .batchId(batchId)
                .feedType(feedType)
                .sourceFile(sourceFile)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();
        metadataRepository.save(metadata);
    }

    @Transactional
    public void recordBatchComplete(String batchId, String feedType, String sourceFile,
                                    long readCount, long writeCount, long skipCount,
                                    long durationMs, String status) {
        Map<String, Long> breakdown = deadLetterService.getFailureBreakdown(batchId);

        metadataRepository.findByBatchId(batchId).ifPresentOrElse(m -> {
            m.setTotalRecords(readCount);
            m.setSucceeded(writeCount);
            m.setFailed(skipCount);
            m.setSkipped(skipCount);
            m.setStatus(status);
            m.setCompletedAt(LocalDateTime.now());
            m.setDurationMs(durationMs);
            m.setFailureBreakdown(breakdown);
            metadataRepository.save(m);
        }, () -> {
            BatchRunMetadata m = BatchRunMetadata.builder()
                    .batchId(batchId).feedType(feedType).sourceFile(sourceFile)
                    .totalRecords(readCount).succeeded(writeCount).failed(skipCount).skipped(skipCount)
                    .status(status).startedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now()).durationMs(durationMs)
                    .failureBreakdown(breakdown)
                    .build();
            metadataRepository.save(m);
        });

        log.info("BATCH_COMPLETE batchId={} feedType={} status={} total={} succeeded={} failed={} durationMs={} breakdown={}",
                batchId, feedType, status, readCount, writeCount, skipCount, durationMs, breakdown);

        printSummaryBox(batchId, feedType, readCount, writeCount, skipCount, breakdown);
    }

    @Transactional
    public void recordBatchFailure(String batchId) {
        metadataRepository.findByBatchId(batchId).ifPresent(m -> {
            m.setStatus("FAILED");
            m.setCompletedAt(LocalDateTime.now());
            metadataRepository.save(m);
        });
    }

    private void printSummaryBox(String batchId, String feedType,
                                 long total, long succeeded, long failed,
                                 Map<String, Long> breakdown) {
        StringBuilder sb = new StringBuilder("\n");
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║              BULKFLOW — BATCH COMPLETE                   ║\n");
        sb.append("╠══════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Batch ID  : %-44s ║%n", truncate(batchId, 44)));
        sb.append(String.format("║  Feed Type : %-44s ║%n", feedType));
        sb.append(String.format("║  Total     : %-44s ║%n", String.format("%,d records", total)));
        sb.append(String.format("║  Succeeded : %-44s ║%n", String.format("%,d", succeeded)));
        sb.append(String.format("║  Failed    : %-44s ║%n", String.format("%,d", failed)));
        if (!breakdown.isEmpty()) {
            sb.append("╠══════════════════════════════════════════════════════════╣\n");
            sb.append("║  Failure Breakdown:                                      ║\n");
            breakdown.forEach((reason, count) ->
                    sb.append(String.format("║    %-22s : %-26s ║%n",
                            reason, String.format("%,d", count))));
        }
        sb.append("╚══════════════════════════════════════════════════════════╝");
        log.info(sb.toString());
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
