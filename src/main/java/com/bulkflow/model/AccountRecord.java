package com.bulkflow.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AccountRecord {
    private String accountId;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private LocalDate dateOfBirth;
    private String phone;
    private BigDecimal creditLimit;
    private String currency;
    private String rowHash;
    private String batchId;
    private String rawLine;

    public AccountRecord() {}

    public AccountRecord(String accountId, String email, String firstName, String lastName,
                         String status, LocalDate dateOfBirth, String phone, BigDecimal creditLimit,
                         String currency, String rowHash, String batchId, String rawLine) {
        this.accountId = accountId; this.email = email; this.firstName = firstName;
        this.lastName = lastName; this.status = status; this.dateOfBirth = dateOfBirth;
        this.phone = phone; this.creditLimit = creditLimit; this.currency = currency;
        this.rowHash = rowHash; this.batchId = batchId; this.rawLine = rawLine;
    }

    public static Builder builder() { return new Builder(); }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getRowHash() { return rowHash; }
    public void setRowHash(String rowHash) { this.rowHash = rowHash; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }

    public static class Builder {
        private final AccountRecord r = new AccountRecord();
        public Builder accountId(String v) { r.accountId = v; return this; }
        public Builder email(String v) { r.email = v; return this; }
        public Builder firstName(String v) { r.firstName = v; return this; }
        public Builder lastName(String v) { r.lastName = v; return this; }
        public Builder status(String v) { r.status = v; return this; }
        public Builder dateOfBirth(LocalDate v) { r.dateOfBirth = v; return this; }
        public Builder phone(String v) { r.phone = v; return this; }
        public Builder creditLimit(BigDecimal v) { r.creditLimit = v; return this; }
        public Builder currency(String v) { r.currency = v; return this; }
        public Builder rowHash(String v) { r.rowHash = v; return this; }
        public Builder batchId(String v) { r.batchId = v; return this; }
        public Builder rawLine(String v) { r.rawLine = v; return this; }
        public AccountRecord build() { return r; }
    }
}
