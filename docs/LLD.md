# BulkFlow — Low-Level Design

## 1. Chunk Size Selection

**Chosen value: 500 records/chunk**

Chunk size is the number of records read, processed, and written in a single database transaction. The tradeoff is between:
- **Memory**: larger chunks hold more `AccountRecord` objects in the JVM heap simultaneously
- **Transaction cost**: smaller chunks commit more frequently (more round-trips, more Spring Batch metadata writes)
- **Failure granularity**: if a chunk partially fails, only that chunk is rolled back; smaller chunks reduce rollback surface

**Heap estimate per chunk:**
```
AccountRecord ≈ 400 bytes (9 String fields + LocalDate + BigDecimal)
chunk_size = 500
heap per chunk ≈ 500 × 400 = 200KB
```
At 10 Hikari pool connections each potentially mid-chunk, peak heap from in-flight chunks ≈ 2MB. Well within typical JVM heap (-Xmx512m minimum recommended). For JSON transactions the estimate is similar.

**Empirical guideline:** For PostgreSQL on local network (<5ms RTT), 500 is the sweet spot. Below 200, transaction overhead dominates. Above 1000, heap pressure increases and a chunk failure on row 999 wastes significant work. Production tuning should run load tests at 200/500/1000 and measure `WRITE_COUNT/second` from Spring Batch step metrics.

**Configuration:** `bulkflow.batch.chunk-size=500` in `application.yml`. Overridden to 100 in `application-test.yml` so unit integration tests run faster.

---

## 2. Skip/Retry Policy Design

### Skip Policy

Spring Batch's `faultTolerant()` API classifies exceptions as skippable, retryable, or fatal.

**Skippable (dead-lettered, batch continues):**
- `ValidationException` — custom exception thrown by `AccountValidator` / `TransactionValidator`. Always skippable; retrying won't fix a structural data problem.
- `FlatFileParseException` — Spring Batch's exception for malformed CSV rows (wrong column count, unescaped quotes). The row is preserved as `fpe.getInput()` in the dead-letter entry.

**Retryable (with exponential backoff):**
- `TransientDataAccessException` — Spring's abstraction over transient database errors: connection pool exhaustion, lock timeout, deadlock. Retried up to `bulkflow.batch.retry-limit=3` times. Backoff starts at `retry-backoff-ms=1000ms` (default Spring Batch `BackOffPolicy`).

**Fatal (chunk fails, job marked FAILED):**
- `DataIntegrityViolationException` — schema violation not caught by application validation. This indicates a code bug (e.g., a field value that passes validation but violates a DB constraint not covered by the validator). Failing the batch here is intentional — it escalates for investigation rather than silently accumulating schema-violating records in dead-letter.
- All other unchecked exceptions — unknown failures should surface, not be swallowed.

**Skip limit:** `Integer.MAX_VALUE` for `ValidationException`. The system never fails a batch due to validation volume. Rationale: if 80% of records fail validation, metrics surface it in seconds; failing the batch would block the valid 20% with no benefit.

### Retry Mechanism

Spring Batch uses a `RetryTemplate` with `SimpleRetryPolicy` (max 3 attempts) and `ExponentialBackOffPolicy` (initial 1s, default multiplier 2x → 1s, 2s, 4s). The retry wraps the entire processor + writer invocation for a chunk. If a transient error occurs during the write phase, the chunk is retried from the processor phase (records are re-processed from the reader checkpoint).

---

## 3. Dead-Letter Schema Design

```sql
CREATE TABLE dead_letter_records (
    id                BIGSERIAL PRIMARY KEY,
    batch_id          VARCHAR(255) NOT NULL,     -- UUID, links to batch_run_metadata
    feed_type         VARCHAR(50)  NOT NULL,     -- 'accounts' | 'transactions'
    raw_record        TEXT         NOT NULL,     -- original unparsed line, never truncated
    failure_reason    VARCHAR(100) NOT NULL,     -- machine code: 'invalid_email'
    failure_field     VARCHAR(100),              -- 'email', 'credit_limit', etc.
    failure_message   TEXT         NOT NULL,     -- human message for triage
    status            VARCHAR(30)  NOT NULL,     -- PENDING | REPROCESSED | PERMANENTLY_FAILED
    created_at        TIMESTAMP    NOT NULL,
    reprocessed_at    TIMESTAMP,
    reprocess_batch_id VARCHAR(255)
);
```

**Design decisions:**

`raw_record TEXT` — the original unparsed line is stored before any field mapping. For CSV, this is the raw comma-delimited string including the bad field. For JSON Lines, this is the raw JSON object. Storing raw avoids the chicken-and-egg problem: if parsing failed, there are no clean fields to store. Operators can copy the raw record, fix it, and resubmit.

`failure_reason VARCHAR(100)` — machine-readable code from the `FailureReason` enum. Not a free-text message. This enables analytic queries: `SELECT failure_reason, COUNT(*) FROM dead_letter_records WHERE batch_id = ? GROUP BY failure_reason`. The codes are stable across releases (part of the API contract); the human message in `failure_message` can change without breaking downstream analytics.

`failure_field VARCHAR(100)` — the specific field that triggered the failure. Combined with `failure_reason`, this tells an operator exactly what to fix in the source system without reading the full error message or re-running validation locally.

`REQUIRES_NEW` transaction propagation — `DeadLetterService.save()` runs in its own transaction. This is the critical isolation guarantee: if a chunk rolls back due to a transient error, the dead-letter write for that chunk's bad records still commits. Without `REQUIRES_NEW`, the dead-letter insert would roll back with the chunk and the bad record would disappear with no trace.

---

## 4. Idempotency Implementation

**Hash computation (post-normalization):**
```java
String content = String.join("|",
    accountId, email, firstName, lastName, status,
    dateOfBirth.toString(), phone, creditLimit.toPlainString(), currency
);
String rowHash = SHA256(content);
```

Fields are hashed after normalization (lowercase email, uppercase status/currency) so that the hash is stable across cosmetic source variations. A re-ingestion of the same record from a different file — even if the source has inconsistent casing — produces the same hash.

**Database enforcement:**
```sql
INSERT INTO accounts (..., row_hash, ...) VALUES (..., :rowHash, ...)
ON CONFLICT (row_hash) DO NOTHING
```

`DO NOTHING` means: if a row with this `row_hash` already exists, silently skip this insert and return success. No exception is raised; the JDBC `executeBatch()` returns an update count of 0 for that row. `JdbcBatchItemWriter` treats update count 0 as acceptable (it does not assert that each row produced exactly 1 insert).

**Edge cases:**

| Scenario | Behavior |
|---|---|
| Same file re-uploaded | All rows hash to the same values → all skipped by ON CONFLICT → 0 new rows inserted, no errors |
| Same record with updated email | Different hash → new row inserted (old row NOT updated — update semantics are out of scope) |
| Same account_id different email | Different hash → both rows would attempt insert; second fails `UNIQUE (account_id)` constraint → `DataIntegrityViolationException` → batch fails (by design — account_id should be unique) |
| Two files with overlapping valid records | Overlap rows silently skipped; unique rows from each file load normally |

---

## 5. Batch Job Isolation

Each job invocation receives a unique `batchId` (`"batch-" + UUID.randomUUID()`). This UUID:
- Flows through `JobParameters` into every processor, listener, and service
- Is set as an MDC context key (`MDC.put("batch_id", batchId)`) so every log line in the job's thread carries it
- Is the primary key for `batch_run_metadata` and the foreign key for `dead_letter_records`

MDC is cleared in a `finally` block in `BatchJobLauncher.launch()` to avoid MDC leakage across jobs running on shared thread pools.

---

## 6. Writer Strategy

`JdbcBatchItemWriter` calls `DataSource.getConnection()`, then for each chunk calls `PreparedStatement.addBatch()` for each item in the chunk, then `executeBatch()`. The underlying PostgreSQL JDBC driver sends all batch statements in a single network packet (when `reWriteBatchedInserts=true` is set — a future optimization). The entire batch executes within Spring Batch's chunk transaction.

**Why not JPA batch insert?** JPA's `@GeneratedValue(SEQUENCE)` strategy issues a separate SELECT next val for each entity before batching inserts, adding N sequence queries per chunk. `JdbcBatchItemWriter` with named parameters bypasses JPA entirely — no entity tracking, no sequence pre-fetch, no first-level cache overhead.

**Hibernate properties** (`spring.jpa.properties.hibernate.jdbc.batch_size=500`, `order_inserts=true`) are set for JPA repositories (dead-letter, metadata) — not for the batch writer, which bypasses JPA.

---

## 7. Concurrency Model

**Single-threaded chunk processing (by design)**

Spring Batch's default step configuration processes chunks sequentially on a single thread. BulkFlow does not use `TaskExecutorPartitioner` or `AsyncItemProcessor`. This is deliberate:

**Why not parallel steps?** The `DuplicateDetector` uses a `ConcurrentHashMap` for intra-batch dedup. Parallel threads writing to the same set would work correctly (the set is thread-safe), but the DuplicateDetector tracks keys from a single logical stream. If two threads process chunks 1 and 3 simultaneously, and chunk 1 contains the canonical record while chunk 3 contains the duplicate, the result depends on which chunk's thread registers the key first. This is not a correctness problem per se, but the dead-letter reason would be non-deterministic (either chunk 1's or chunk 3's record could be labeled "duplicate"). For audit clarity, sequential processing is preferred.

**Throughput path:** Sequential at chunk_size=500 already achieves ~100k rows/min. For higher throughput, the correct approach is **file partitioning** (partition the source file into N segments, process each segment in a separate step partition with its own DuplicateDetector) rather than intra-file parallelism. This is documented as "What I'd Add Next" in the README.
