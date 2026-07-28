package com.bulkflow.unit;

import com.bulkflow.model.AccountRecord;
import com.bulkflow.model.FailureReason;
import com.bulkflow.validation.AccountValidator;
import com.bulkflow.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class AccountValidatorTest {

    private AccountValidator validator;

    @BeforeEach
    void setup() {
        validator = new AccountValidator();
    }

    private AccountRecord validRecord() {
        return AccountRecord.builder()
                .accountId("acc_00001")
                .email("alice.smith@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .status("ACTIVE")
                .phone("+1-555-123-4567")
                .currency("USD")
                .creditLimit(new BigDecimal("5000.00"))
                .rawLine("acc_00001,alice.smith@example.com,Alice,Smith,ACTIVE,,+1-555-123-4567,5000.00,USD")
                .build();
    }

    @Test
    void valid_record_passes() {
        assertThatCode(() -> validator.validate(validRecord())).doesNotThrowAnyException();
    }

    @Test
    void missing_account_id_throws_missing_field() {
        AccountRecord r = validRecord();
        r.setAccountId(null);
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assert ve.getFailureReason() == FailureReason.MISSING_REQUIRED_FIELD;
                    assert "account_id".equals(ve.getFailureField());
                });
    }

    @Test
    void blank_email_throws_missing_field() {
        AccountRecord r = validRecord();
        r.setEmail("   ");
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assert ve.getFailureReason() == FailureReason.MISSING_REQUIRED_FIELD;
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "missing@", "@nodomain.com", "no_at_sign", "double@@at.com"})
    void invalid_email_format_throws(String email) {
        AccountRecord r = validRecord();
        r.setEmail(email);
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assert ve.getFailureReason() == FailureReason.INVALID_EMAIL;
                    assert "email".equals(ve.getFailureField());
                });
    }

    @Test
    void invalid_status_throws() {
        AccountRecord r = validRecord();
        r.setStatus("UNKNOWN_STATUS");
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assert ve.getFailureReason() == FailureReason.INVALID_ENUM_VALUE;
                    assert "status".equals(ve.getFailureField());
                });
    }

    @Test
    void invalid_currency_throws() {
        AccountRecord r = validRecord();
        r.setCurrency("XYZ");
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assert ve.getFailureReason() == FailureReason.INVALID_ENUM_VALUE;
                    assert "currency".equals(ve.getFailureField());
                });
    }

    @Test
    void negative_credit_limit_throws() {
        AccountRecord r = validRecord();
        r.setCreditLimit(new BigDecimal("-100.00"));
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException ve = (ValidationException) ex;
                    assert ve.getFailureReason() == FailureReason.INVALID_NUMERIC;
                    assert "credit_limit".equals(ve.getFailureField());
                });
    }

    @Test
    void null_optional_fields_pass() {
        AccountRecord r = validRecord();
        r.setPhone(null);
        r.setCreditLimit(null);
        r.setStatus(null);
        r.setCurrency(null);
        assertThatCode(() -> validator.validate(r)).doesNotThrowAnyException();
    }

    @Test
    void missing_first_name_throws() {
        AccountRecord r = validRecord();
        r.setFirstName("");
        assertThatThrownBy(() -> validator.validate(r))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    assert ((ValidationException) ex).getFailureField().equals("first_name");
                });
    }
}
