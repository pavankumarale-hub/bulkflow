package com.bulkflow.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "batch_run_metadata")
public class BatchRunMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, unique = true)
    private String batchId;

    @Column(name = "feed_type", nullable = false)
    private String feedType;

    @Column(name = "source_file", nullable = false)
    private String sourceFile;

    @Column(name = "total_records")
    private long totalRecords;

    @Column(name = "succeeded")
    private long succeeded;

    @Column(name = "failed")
    private long failed;

    @Column(name = "skipped")
    private long skipped;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure_breakdown", columnDefinition = "jsonb")
    private Map<String, Long> failureBreakdown;

    public BatchRunMetadata() {}

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = LocalDateTime.now();
        if (status == null) status = "RUNNING";
    }

    public static Builder builder() { return new Builder(); }

    public Long getId() { return id; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String v) { this.batchId = v; }
    public String getFeedType() { return feedType; }
    public void setFeedType(String v) { this.feedType = v; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String v) { this.sourceFile = v; }
    public long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(long v) { this.totalRecords = v; }
    public long getSucceeded() { return succeeded; }
    public void setSucceeded(long v) { this.succeeded = v; }
    public long getFailed() { return failed; }
    public void setFailed(long v) { this.failed = v; }
    public long getSkipped() { return skipped; }
    public void setSkipped(long v) { this.skipped = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime v) { this.startedAt = v; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long v) { this.durationMs = v; }
    public Map<String, Long> getFailureBreakdown() { return failureBreakdown; }
    public void setFailureBreakdown(Map<String, Long> v) { this.failureBreakdown = v; }

    public static class Builder {
        private final BatchRunMetadata r = new BatchRunMetadata();
        public Builder batchId(String v) { r.batchId = v; return this; }
        public Builder feedType(String v) { r.feedType = v; return this; }
        public Builder sourceFile(String v) { r.sourceFile = v; return this; }
        public Builder totalRecords(long v) { r.totalRecords = v; return this; }
        public Builder succeeded(long v) { r.succeeded = v; return this; }
        public Builder failed(long v) { r.failed = v; return this; }
        public Builder skipped(long v) { r.skipped = v; return this; }
        public Builder status(String v) { r.status = v; return this; }
        public Builder startedAt(LocalDateTime v) { r.startedAt = v; return this; }
        public Builder completedAt(LocalDateTime v) { r.completedAt = v; return this; }
        public Builder durationMs(Long v) { r.durationMs = v; return this; }
        public Builder failureBreakdown(Map<String, Long> v) { r.failureBreakdown = v; return this; }
        public BatchRunMetadata build() { return r; }
    }
}
