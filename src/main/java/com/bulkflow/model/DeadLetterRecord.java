package com.bulkflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_records")
public class DeadLetterRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @Column(name = "feed_type", nullable = false)
    private String feedType;

    @Column(name = "raw_record", nullable = false, columnDefinition = "TEXT")
    private String rawRecord;

    @Column(name = "failure_reason", nullable = false)
    private String failureReason;

    @Column(name = "failure_field")
    private String failureField;

    @Column(name = "failure_message", nullable = false, columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reprocessed_at")
    private LocalDateTime reprocessedAt;

    @Column(name = "reprocess_batch_id")
    private String reprocessBatchId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private DeadLetterStatus status;

    public DeadLetterRecord() {}

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = DeadLetterStatus.PENDING;
    }

    public static Builder builder() { return new Builder(); }

    public Long getId() { return id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String v) { this.batchId = v; }
    public String getFeedType() { return feedType; }
    public void setFeedType(String v) { this.feedType = v; }
    public String getRawRecord() { return rawRecord; }
    public void setRawRecord(String v) { this.rawRecord = v; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public String getFailureField() { return failureField; }
    public void setFailureField(String v) { this.failureField = v; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String v) { this.failureMessage = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getReprocessedAt() { return reprocessedAt; }
    public void setReprocessedAt(LocalDateTime v) { this.reprocessedAt = v; }
    public String getReprocessBatchId() { return reprocessBatchId; }
    public void setReprocessBatchId(String v) { this.reprocessBatchId = v; }
    public DeadLetterStatus getStatus() { return status; }
    public void setStatus(DeadLetterStatus v) { this.status = v; }

    public static class Builder {
        private final DeadLetterRecord r = new DeadLetterRecord();
        public Builder batchId(String v) { r.batchId = v; return this; }
        public Builder feedType(String v) { r.feedType = v; return this; }
        public Builder rawRecord(String v) { r.rawRecord = v; return this; }
        public Builder failureReason(String v) { r.failureReason = v; return this; }
        public Builder failureField(String v) { r.failureField = v; return this; }
        public Builder failureMessage(String v) { r.failureMessage = v; return this; }
        public Builder status(DeadLetterStatus v) { r.status = v; return this; }
        public DeadLetterRecord build() { return r; }
    }

    public enum DeadLetterStatus {
        PENDING, REPROCESSED, PERMANENTLY_FAILED
    }
}
