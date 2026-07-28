package com.bulkflow.batch;

import com.bulkflow.model.AccountRecord;
import com.bulkflow.model.FailureReason;
import com.bulkflow.transform.AccountTransformer;
import com.bulkflow.validation.AccountValidator;
import com.bulkflow.validation.DuplicateDetector;
import com.bulkflow.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class AccountItemProcessor implements ItemProcessor<AccountRecord, AccountRecord> {

    private static final Logger log = LoggerFactory.getLogger(AccountItemProcessor.class);

    private final AccountValidator validator;
    private final AccountTransformer transformer;
    private final DuplicateDetector duplicateDetector;
    private String batchId;

    public AccountItemProcessor(AccountValidator validator,
                                AccountTransformer transformer,
                                DuplicateDetector duplicateDetector) {
        this.validator = validator;
        this.transformer = transformer;
        this.duplicateDetector = duplicateDetector;
    }

    @BeforeStep
    public void beforeStep(StepExecution stepExecution) {
        this.batchId = stepExecution.getJobParameters().getString("batchId");
        duplicateDetector.reset();
        log.info("AccountItemProcessor ready: batchId={}", batchId);
    }

    @Override
    public AccountRecord process(AccountRecord item) throws Exception {
        validator.validate(item);
        AccountRecord transformed = transformer.transform(item);
        transformed.setBatchId(batchId);

        if (duplicateDetector.isDuplicate(transformed.getAccountId())) {
            throw new ValidationException(
                    FailureReason.DUPLICATE_IN_BATCH, "account_id",
                    "Duplicate account_id within batch: " + transformed.getAccountId(),
                    transformed.getRawLine());
        }
        return transformed;
    }
}
