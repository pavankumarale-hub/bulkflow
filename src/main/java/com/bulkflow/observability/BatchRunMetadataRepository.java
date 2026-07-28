package com.bulkflow.observability;

import com.bulkflow.model.BatchRunMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BatchRunMetadataRepository extends JpaRepository<BatchRunMetadata, Long> {

    Optional<BatchRunMetadata> findByBatchId(String batchId);

    Page<BatchRunMetadata> findByFeedType(String feedType, Pageable pageable);

    @Query("SELECT SUM(m.succeeded) FROM BatchRunMetadata m WHERE m.status = 'COMPLETED'")
    Long totalSucceeded();

    @Query("SELECT SUM(m.failed) FROM BatchRunMetadata m WHERE m.status = 'COMPLETED'")
    Long totalFailed();

    @Query("SELECT COUNT(m) FROM BatchRunMetadata m WHERE m.status = 'COMPLETED'")
    Long completedBatchCount();
}
