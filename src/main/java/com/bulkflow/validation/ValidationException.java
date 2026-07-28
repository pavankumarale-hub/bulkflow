package com.bulkflow.validation;

import com.bulkflow.model.FailureReason;

public class ValidationException extends RuntimeException {

    private final FailureReason failureReason;
    private final String failureField;
    private final String rawRecord;

    public ValidationException(FailureReason reason, String field, String message, String rawRecord) {
        super(message);
        this.failureReason = reason;
        this.failureField = field;
        this.rawRecord = rawRecord;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public String getFailureField() {
        return failureField;
    }

    public String getRawRecord() {
        return rawRecord;
    }
}
