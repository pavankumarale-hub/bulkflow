package com.bulkflow.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionRecord {
    private String transactionId;
    private String accountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private LocalDate transactionDate;
    private String description;
    private String rowHash;
    private String batchId;
    private String rawLine;

    public TransactionRecord() {}

    public static Builder builder() { return new Builder(); }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String v) { this.transactionId = v; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String v) { this.accountId = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String v) { this.transactionType = v; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate v) { this.transactionDate = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getRowHash() { return rowHash; }
    public void setRowHash(String v) { this.rowHash = v; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String v) { this.batchId = v; }
    public String getRawLine() { return rawLine; }
    public void setRawLine(String v) { this.rawLine = v; }

    public static class Builder {
        private final TransactionRecord r = new TransactionRecord();
        public Builder transactionId(String v) { r.transactionId = v; return this; }
        public Builder accountId(String v) { r.accountId = v; return this; }
        public Builder amount(BigDecimal v) { r.amount = v; return this; }
        public Builder currency(String v) { r.currency = v; return this; }
        public Builder transactionType(String v) { r.transactionType = v; return this; }
        public Builder transactionDate(LocalDate v) { r.transactionDate = v; return this; }
        public Builder description(String v) { r.description = v; return this; }
        public Builder rowHash(String v) { r.rowHash = v; return this; }
        public Builder batchId(String v) { r.batchId = v; return this; }
        public Builder rawLine(String v) { r.rawLine = v; return this; }
        public TransactionRecord build() { return r; }
    }
}
