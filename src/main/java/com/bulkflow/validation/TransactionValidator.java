package com.bulkflow.validation;

import com.bulkflow.model.FailureReason;
import com.bulkflow.model.TransactionRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class TransactionValidator {

    private static final Set<String> VALID_TYPES =
            Set.of("CREDIT", "DEBIT", "TRANSFER", "REFUND", "FEE", "ADJUSTMENT");
    private static final Set<String> VALID_CURRENCIES =
            Set.of("USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "INR");

    public void validate(TransactionRecord record) {
        String raw = record.getRawLine();

        assertNotBlank(record.getTransactionId(), "transaction_id", raw);
        assertNotBlank(record.getAccountId(), "account_id", raw);
        assertNotBlank(record.getTransactionType(), "transaction_type", raw);

        if (record.getTransactionDate() == null) {
            throw new ValidationException(
                    FailureReason.MISSING_REQUIRED_FIELD, "transaction_date",
                    "Required field 'transaction_date' is missing", raw);
        }

        if (record.getAmount() == null) {
            throw new ValidationException(
                    FailureReason.MISSING_REQUIRED_FIELD, "amount",
                    "Required field 'amount' is missing", raw);
        }

        if (!VALID_TYPES.contains(record.getTransactionType().toUpperCase())) {
            throw new ValidationException(
                    FailureReason.INVALID_ENUM_VALUE, "transaction_type",
                    "Invalid transaction_type: " + record.getTransactionType()
                            + ". Allowed: " + VALID_TYPES, raw);
        }

        if (record.getCurrency() != null && !record.getCurrency().isBlank()
                && !VALID_CURRENCIES.contains(record.getCurrency().toUpperCase())) {
            throw new ValidationException(
                    FailureReason.INVALID_ENUM_VALUE, "currency",
                    "Invalid currency: " + record.getCurrency() + ". Allowed: " + VALID_CURRENCIES, raw);
        }

        if (record.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    FailureReason.INVALID_NUMERIC, "amount",
                    "Transaction amount must be positive: " + record.getAmount(), raw);
        }
    }

    private void assertNotBlank(String value, String fieldName, String raw) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    FailureReason.MISSING_REQUIRED_FIELD, fieldName,
                    "Required field '" + fieldName + "' is missing or blank", raw);
        }
    }
}
