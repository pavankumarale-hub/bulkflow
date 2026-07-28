package com.bulkflow.deadletter;

import com.bulkflow.model.DeadLetterRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterRecord, Long> {

    Page<DeadLetterRecord> findByBatchId(String batchId, Pageable pageable);

    Page<DeadLetterRecord> findByFeedTypeAndStatus(
            String feedType, DeadLetterRecord.DeadLetterStatus status, Pageable pageable);

    List<DeadLetterRecord> findByBatchIdAndStatus(
            String batchId, DeadLetterRecord.DeadLetterStatus status);

    @Query("""
            SELECT d.failureReason, COUNT(d)
            FROM DeadLetterRecord d
            WHERE d.batchId = :batchId
            GROUP BY d.failureReason
            """)
    List<Object[]> countByFailureReasonForBatch(@Param("batchId") String batchId);

    long countByBatchId(String batchId);
}
