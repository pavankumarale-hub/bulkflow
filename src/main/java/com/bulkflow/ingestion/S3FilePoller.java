package com.bulkflow.ingestion;

import com.bulkflow.batch.BatchJobLauncher;
import com.bulkflow.config.BulkFlowProperties;
import io.minio.*;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
public class S3FilePoller {

    private static final Logger log = LoggerFactory.getLogger(S3FilePoller.class);

    private final MinioClient minioClient;
    private final BulkFlowProperties props;
    private final BatchJobLauncher jobLauncher;

    public S3FilePoller(MinioClient minioClient, BulkFlowProperties props, BatchJobLauncher jobLauncher) {
        this.minioClient = minioClient;
        this.props = props;
        this.jobLauncher = jobLauncher;
    }

    @Scheduled(fixedDelayString = "${bulkflow.poller.interval-ms:30000}")
    public void poll() {
        if (!props.getPoller().isEnabled()) return;

        String bucket = props.getMinio().getInboxBucket();
        log.debug("Polling MinIO bucket: {}", bucket);

        List<String> keys = listObjects(bucket);
        if (keys.isEmpty()) {
            log.debug("No new files in inbox");
            return;
        }
        for (String key : keys) processObject(bucket, key);
    }

    private List<String> listObjects(String bucket) {
        List<String> keys = new ArrayList<>();
        try {
            for (Result<Item> result : minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).build())) {
                Item item = result.get();
                if (!item.isDir()) keys.add(item.objectName());
            }
        } catch (Exception e) {
            log.error("Failed to list objects from bucket {}", bucket, e);
        }
        return keys;
    }

    private void processObject(String bucket, String objectKey) {
        FeedType feedType = FeedType.fromFilename(objectKey);
        if (feedType == null) {
            log.warn("Unknown feed type for file: {} — skipping", objectKey);
            return;
        }
        try {
            Path localPath = Files.createTempFile("bulkflow-",
                    "-" + objectKey.replaceAll("[/\\\\]", "_"));
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                Files.copy(stream, localPath, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Downloaded: bucket={} key={} localPath={}", bucket, objectKey, localPath);
            String batchId = jobLauncher.launch(objectKey, localPath.toString(), feedType);
            log.info("Batch launched: batchId={} file={}", batchId, objectKey);
            moveToProcessed(bucket, objectKey);
            Files.deleteIfExists(localPath);
        } catch (Exception e) {
            log.error("Failed to process: bucket={} key={}", bucket, objectKey, e);
        }
    }

    private void moveToProcessed(String sourceBucket, String objectKey) {
        String processedBucket = props.getMinio().getProcessedBucket();
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(processedBucket).object(objectKey)
                    .source(CopySource.builder().bucket(sourceBucket).object(objectKey).build())
                    .build());
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(sourceBucket).object(objectKey).build());
            log.info("Archived: {} → {}/{}", objectKey, processedBucket, objectKey);
        } catch (Exception e) {
            log.warn("Could not archive to processed bucket: {}", objectKey, e);
        }
    }
}
