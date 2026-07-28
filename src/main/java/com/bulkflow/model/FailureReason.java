package com.bulkflow.model;

public enum FailureReason {
    MISSING_REQUIRED_FIELD("missing_field"),
    INVALID_EMAIL("invalid_email"),
    INVALID_DATE_FORMAT("invalid_date_format"),
    INVALID_ENUM_VALUE("invalid_enum_value"),
    INVALID_NUMERIC("invalid_numeric"),
    INVALID_PHONE("invalid_phone"),
    DUPLICATE_IN_BATCH("duplicate_in_batch"),
    DUPLICATE_IN_DATABASE("duplicate_in_database"),
    REFERENTIAL_INTEGRITY("referential_integrity"),
    PARSE_ERROR("parse_error"),
    UNKNOWN("unknown");

    private final String code;

    FailureReason(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
