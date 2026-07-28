package com.bulkflow.validation;

import com.bulkflow.model.AccountRecord;
import com.bulkflow.model.FailureReason;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AccountValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[+]?[0-9\\-\\s().]{7,20}$");
    private static final Set<String> VALID_STATUSES =
            Set.of("ACTIVE", "INACTIVE", "PENDING", "SUSPENDED");
    private static final Set<String> VALID_CURRENCIES =
            Set.of("USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "INR");

    public void validate(AccountRecord record) {
        String raw = record.getRawLine();

        assertNotBlank(record.getAccountId(), "account_id", raw);
        assertNotBlank(record.getEmail(), "email", raw);
        assertNotBlank(record.getFirstName(), "first_name", raw);
        assertNotBlank(record.getLastName(), "last_name", raw);

        if (!EMAIL_PATTERN.matcher(record.getEmail()).matches()) {
            throw new ValidationException(
                    FailureReason.INVALID_EMAIL, "email",
                    "Invalid email format: " + record.getEmail(), raw);
        }

        if (record.getPhone() != null && !record.getPhone().isBlank()
                && !PHONE_PATTERN.matcher(record.getPhone()).matches()) {
            throw new ValidationException(
                    FailureReason.INVALID_PHONE, "phone",
                    "Invalid phone format: " + record.getPhone(), raw);
        }

        if (record.getStatus() != null && !record.getStatus().isBlank()
                && !VALID_STATUSES.contains(record.getStatus().toUpperCase())) {
            throw new ValidationException(
                    FailureReason.INVALID_ENUM_VALUE, "status",
                    "Invalid status: " + record.getStatus() + ". Allowed: " + VALID_STATUSES, raw);
        }

        if (record.getCurrency() != null && !record.getCurrency().isBlank()
                && !VALID_CURRENCIES.contains(record.getCurrency().toUpperCase())) {
            throw new ValidationException(
                    FailureReason.INVALID_ENUM_VALUE, "currency",
                    "Invalid currency: " + record.getCurrency() + ". Allowed: " + VALID_CURRENCIES, raw);
        }

        if (record.getCreditLimit() != null && record.getCreditLimit().signum() < 0) {
            throw new ValidationException(
                    FailureReason.INVALID_NUMERIC, "credit_limit",
                    "Credit limit cannot be negative: " + record.getCreditLimit(), raw);
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
