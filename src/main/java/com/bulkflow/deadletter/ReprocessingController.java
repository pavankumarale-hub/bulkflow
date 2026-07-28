package com.bulkflow.deadletter;

import com.bulkflow.model.DeadLetterRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dead-letter")
public class ReprocessingController {

    private final DeadLetterRepository repository;
    private final DeadLetterService service;

    public ReprocessingController(DeadLetterRepository repository, DeadLetterService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public Page<DeadLetterRecord> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String feedType) {

        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (batchId != null) return repository.findByBatchId(batchId, pr);
        if (feedType != null) return repository.findByFeedTypeAndStatus(
                feedType, DeadLetterRecord.DeadLetterStatus.PENDING, pr);
        return repository.findAll(pr);
    }

    @GetMapping("/{batchId}/breakdown")
    public Map<String, Long> breakdown(@PathVariable String batchId) {
        return service.getFailureBreakdown(batchId);
    }

    @PostMapping("/{batchId}/reprocess")
    public ResponseEntity<Map<String, Object>> reprocess(
            @PathVariable String batchId,
            @RequestParam String reprocessBatchId) {
        int count = service.markReprocessed(batchId, reprocessBatchId);
        return ResponseEntity.ok(Map.of(
                "batchId", batchId,
                "reprocessBatchId", reprocessBatchId,
                "markedForReprocessing", count
        ));
    }
}
