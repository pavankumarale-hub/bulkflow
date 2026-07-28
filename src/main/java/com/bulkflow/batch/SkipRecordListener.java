package com.bulkflow.batch;

import com.bulkflow.deadletter.DeadLetterService;
import com.bulkflow.model.AccountRecord;
import com.bulkflow.model.FailureReason;
import com.bulkflow.model.TransactionRecord;
import com.bulkflow.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.stereotype.Component;

@Component
public class SkipRecordListener<T> implements SkipListener<T, T> {

    private static final Logger log = LoggerFactory.getLogger(SkipRecordListener.class);

    private final DeadLetterService deadLetterService;
    private String batchId;
    private String feedType;

    public SkipRecordListener(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.batchId = stepExecution.getJobParameters().getString("batchId");
        this.feedType = stepExecution.getJobParameters().getString("feedType");
    }

    @Override
    public void onSkipInRead(Throwable t) {
        String raw = (t instanceof FlatFileParseException fpe) ? fpe.getInput() : t.getMessage();
        log.warn("SKIP_READ batchId={} reason={} msg={}", batchId, FailureReason.PARSE_ERROR.getCode(), t.getMessage());
        deadLetterService.save(batchId, feedType, raw, FailureReason.PARSE_ERROR, null, t.getMessage());
    }

    @Override
    public void onSkipInProcess(T item, Throwable t) {
        String raw = extractRaw(item);
        FailureReason reason;
        String field = null;
        String message = t.getMessage();

        if (t instanceof ValidationException ve) {
            reason = ve.getFailureReason();
            field = ve.getFailureField();
            if (ve.getRawRecord() != null) raw = ve.getRawRecord();
        } else {
            reason = FailureReason.UNKNOWN;
        }

        log.warn("SKIP_PROCESS batchId={} reason={} field={} msg={}", batchId, reason.getCode(), field, message);
        deadLetterService.save(batchId, feedType, raw, reason, field, message);
    }

    @Override
    public void onSkipInWrite(T item, Throwable t) {
        String raw = extractRaw(item);
        log.warn("SKIP_WRITE batchId={} reason={} msg={}", batchId, FailureReason.DUPLICATE_IN_DATABASE.getCode(), t.getMessage());
        deadLetterService.save(batchId, feedType, raw, FailureReason.DUPLICATE_IN_DATABASE, null, t.getMessage());
    }

    private String extractRaw(T item) {
        if (item instanceof AccountRecord r) return r.getRawLine() != null ? r.getRawLine() : "";
        if (item instanceof TransactionRecord r) return r.getRawLine() != null ? r.getRawLine() : "";
        return item != null ? item.toString() : "";
    }
}
