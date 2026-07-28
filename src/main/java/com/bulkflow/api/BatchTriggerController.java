package com.bulkflow.api;

import com.bulkflow.batch.BatchJobLauncher;
import com.bulkflow.ingestion.FeedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/batch")
public class BatchTriggerController {

    private static final Logger log = LoggerFactory.getLogger(BatchTriggerController.class);

    private final BatchJobLauncher jobLauncher;

    public BatchTriggerController(BatchJobLauncher jobLauncher) {
        this.jobLauncher = jobLauncher;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("feedType") String feedTypeParam) {

        FeedType feedType;
        try {
            feedType = FeedType.valueOf(feedTypeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Invalid feedType: " + feedTypeParam + ". Use ACCOUNTS or TRANSACTIONS"));
        }

        try {
            String originalName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "upload";
            Path tempFile = Files.createTempFile("bulkflow-upload-", "-" + originalName);
            file.transferTo(tempFile);

            String batchId = jobLauncher.launch(originalName, tempFile.toString(), feedType);

            return ResponseEntity.accepted().body(Map.of(
                    "batchId", batchId,
                    "feedType", feedType.name(),
                    "message", "Batch job launched — poll /api/metrics/batches/" + batchId + " for status"
            ));
        } catch (Exception e) {
            log.error("Upload trigger failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
