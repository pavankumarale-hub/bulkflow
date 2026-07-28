package com.bulkflow.batch;

import com.bulkflow.model.FailureReason;
import com.bulkflow.model.TransactionRecord;
import com.bulkflow.transform.TransactionTransformer;
import com.bulkflow.validation.DuplicateDetector;
import com.bulkflow.validation.TransactionValidator;
import com.bulkflow.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class TransactionItemProcessor implements ItemProcessor<TransactionRecord, TransactionRecord> {

    private static final Logger log = LoggerFactory.getLogger(TransactionItemProcessor.class);

    private final TransactionValidator validator;
    private final TransactionTransformer transformer;
    private final DuplicateDetector duplicateDetector;
    private String batchId;

    public TransactionItemProcessor(TransactionValidator validator,
                                    TransactionTransformer transformer,
                                    DuplicateDetector duplicateDetector) {
        this.validator = validator;
        this.transformer = transformer;
        this.duplicateDetector = duplicateDetector;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.batchId = stepExecution.getJobParameters().getString("batchId");
        duplicateDetector.reset();
        log.info("TransactionItemProcessor ready: batchId={}", batchId);
    }

    @Override
    public TransactionRecord process(TransactionRecord item) throws Exception {
        validator.validate(item);
        TransactionRecord transformed = transformer.transform(item);
        transformed.setBatchId(batchId);

        if (duplicateDetector.isDuplicate(transformed.getTransactionId())) {
            throw new ValidationException(
                    FailureReason.DUPLICATE_IN_BATCH, "transaction_id",
                    "Duplicate transaction_id within batch: " + transformed.getTransactionId(),
                    transformed.getRawLine());
        }
        return transformed;
    }
}
