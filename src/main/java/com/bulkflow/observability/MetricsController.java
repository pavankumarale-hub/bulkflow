package com.bulkflow.observability;

import com.bulkflow.model.BatchRunMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final BatchRunMetadataRepository repository;

    public MetricsController(BatchRunMetadataRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        Long succeeded = repository.totalSucceeded();
        Long failed = repository.totalFailed();
        Long batches = repository.completedBatchCount();
        long total = (succeeded != null ? succeeded : 0L) + (failed != null ? failed : 0L);
        double rate = total > 0
                ? ((double)(succeeded != null ? succeeded : 0L) / total) * 100.0 : 0.0;

        return ResponseEntity.ok(Map.of(
                "completedBatches", batches != null ? batches : 0L,
                "totalRecordsProcessed", total,
                "totalSucceeded", succeeded != null ? succeeded : 0L,
                "totalFailed", failed != null ? failed : 0L,
                "overallSuccessRatePct", Math.round(rate * 100.0) / 100.0
        ));
    }

    @GetMapping("/batches")
    public Page<BatchRunMetadata> batches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String feedType) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return feedType != null ? repository.findByFeedType(feedType, pr) : repository.findAll(pr);
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchRunMetadata> batch(@PathVariable String batchId) {
        return repository.findByBatchId(batchId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
