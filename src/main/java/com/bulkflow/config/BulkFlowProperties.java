package com.bulkflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bulkflow")
public class BulkFlowProperties {

    private Batch batch = new Batch();
    private Poller poller = new Poller();
    private Minio minio = new Minio();

    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }
    public Poller getPoller() { return poller; }
    public void setPoller(Poller poller) { this.poller = poller; }
    public Minio getMinio() { return minio; }
    public void setMinio(Minio minio) { this.minio = minio; }

    public static class Batch {
        private int chunkSize = 500;
        private int skipLimit = 1_000_000;
        private int retryLimit = 3;
        private long retryBackoffMs = 1000;

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int v) { this.chunkSize = v; }
        public int getSkipLimit() { return skipLimit; }
        public void setSkipLimit(int v) { this.skipLimit = v; }
        public int getRetryLimit() { return retryLimit; }
        public void setRetryLimit(int v) { this.retryLimit = v; }
        public long getRetryBackoffMs() { return retryBackoffMs; }
        public void setRetryBackoffMs(long v) { this.retryBackoffMs = v; }
    }

    public static class Poller {
        private long intervalMs = 30_000;
        private boolean enabled = true;

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long v) { this.intervalMs = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }

    public static class Minio {
        private String inboxBucket = "bulkflow-inbox";
        private String processedBucket = "bulkflow-processed";
        private String deadLetterBucket = "bulkflow-dead-letter";

        public String getInboxBucket() { return inboxBucket; }
        public void setInboxBucket(String v) { this.inboxBucket = v; }
        public String getProcessedBucket() { return processedBucket; }
        public void setProcessedBucket(String v) { this.processedBucket = v; }
        public String getDeadLetterBucket() { return deadLetterBucket; }
        public void setDeadLetterBucket(String v) { this.deadLetterBucket = v; }
    }
}
