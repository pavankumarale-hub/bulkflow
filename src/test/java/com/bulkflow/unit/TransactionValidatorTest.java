package com.bulkflow.unit;

import com.bulkflow.model.FailureReason;
import com.bulkflow.model.TransactionRecord;
import com.bulkflow.validation.TransactionValidator;
import com.bulkflow.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class TransactionValidatorTest {

    private TransactionValidator validator;

    @BeforeEach
    void setup() {
        validator = new TransactionValidator();
    }

    private TransactionRecord validRecord() {
        return TransactionRecord.builder()
                .transactionId("txn_000001")
                .accountId("acc_00001")
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .transactionType("CREDIT")
                .transactionDate(LocalDate.of(2024, 1, 15))
                .description("Direct deposit")
                .rawLine("{\"transactionId\":\"txn_000001\"}")
                .build();
    }

    @Test
    void valid_record_passes() {
        assertThatCode(() -> validator.validate(validRecord())).doesNotThrowAnyException();
    }

    @Test
    void missing_transaction_id_throws() {
        TransactionRecord r = validRecord();
        r.setTransactionId(null);
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getFailureReason())
                .isEqualTo(FailureReason.MISSING_REQUIRED_FIELD);
    }

    @Test
    void missing_amount_throws() {
        TransactionRecord r = validRecord();
        r.setAmount(null);
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getFailureReason())
                .isEqualTo(FailureReason.MISSING_REQUIRED_FIELD);
    }

    @Test
    void zero_amount_throws() {
        TransactionRecord r = validRecord();
        r.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getFailureReason())
                .isEqualTo(FailureReason.INVALID_NUMERIC);
    }

    @Test
    void negative_amount_throws() {
        TransactionRecord r = validRecord();
        r.setAmount(new BigDecimal("-50.00"));
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getFailureReason())
                .isEqualTo(FailureReason.INVALID_NUMERIC);
    }

    @Test
    void invalid_transaction_type_throws() {
        TransactionRecord r = validRecord();
        r.setTransactionType("UNKNOWN_TYPE");
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getFailureReason())
                .isEqualTo(FailureReason.INVALID_ENUM_VALUE);
    }

    @Test
    void missing_transaction_date_throws() {
        TransactionRecord r = validRecord();
        r.setTransactionDate(null);
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getFailureReason())
                .isEqualTo(FailureReason.MISSING_REQUIRED_FIELD);
    }

    @Test
    void all_valid_transaction_types_accepted() {
        for (String type : new String[]{"CREDIT", "DEBIT", "TRANSFER", "REFUND", "FEE", "ADJUSTMENT"}) {
            TransactionRecord r = validRecord();
            r.setTransactionType(type);
            assertThatCode(() -> validator.validate(r))
                    .as("Type %s should be valid", type)
                    .doesNotThrowAnyException();
        }
    }
}
