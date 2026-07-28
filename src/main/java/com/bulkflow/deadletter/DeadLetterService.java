package com.bulkflow.deadletter;

import com.bulkflow.model.DeadLetterRecord;
import com.bulkflow.model.FailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    private final DeadLetterRepository repository;

    public DeadLetterService(DeadLetterRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(String batchId, String feedType, String rawRecord,
                     FailureReason reason, String field, String message) {
        DeadLetterRecord record = DeadLetterRecord.builder()
                .batchId(batchId)
                .feedType(feedType)
                .rawRecord(rawRecord != null ? rawRecord : "")
                .failureReason(reason.getCode())
                .failureField(field)
                .failureMessage(message != null ? message : "")
                .status(DeadLetterRecord.DeadLetterStatus.PENDING)
                .build();
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getFailureBreakdown(String batchId) {
        return repository.countByFailureReasonForBatch(batchId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }

    @Transactional
    public int markReprocessed(String batchId, String reprocessBatchId) {
        List<DeadLetterRecord> pending = repository.findByBatchIdAndStatus(
                batchId, DeadLetterRecord.DeadLetterStatus.PENDING);
        pending.forEach(r -> {
            r.setStatus(DeadLetterRecord.DeadLetterStatus.REPROCESSED);
            r.setReprocessedAt(LocalDateTime.now());
            r.setReprocessBatchId(reprocessBatchId);
        });
        repository.saveAll(pending);
        log.info("Marked {} dead-letter records as reprocessed: originalBatch={} reprocessBatch={}",
                pending.size(), batchId, reprocessBatchId);
        return pending.size();
    }
}
