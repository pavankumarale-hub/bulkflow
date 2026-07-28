# ADR 0003 — Failure Isolation: Spring Batch Skip Policy with Dead-Letter Store

**Status:** Accepted  
**Date:** 2024-01

## Context

A batch of 100,000 records will contain some proportion of malformed records. Three fundamental approaches exist:

### Option A: Fail the Batch on First Error
The simplest implementation: any validation failure propagates as an exception and rolls back the entire chunk, then fails the job. Problems: one typo in one record destroys the entire batch. For 100,000 records, this is operationally unacceptable — operators would be stuck in a loop of fix-one-error/re-run until all records pass, which is the same problem that bulk ingestion pipelines are built to solve.

### Option B: Silent Filtering
Silently discard invalid records and proceed with valid ones. Valid records land in the target table; bad records disappear. This is operationally dangerous: operators have no visibility into what was dropped, cannot reprocess dropped records, and cannot distinguish "all 100k records loaded successfully" from "88k records loaded, 12k silently dropped." This approach is explicitly rejected as it eliminates the audit trail.

### Option C: Skip + Dead-Letter (chosen)
Invalid records are isolated into a queryable, reprocessable dead-letter store. Valid records continue processing in the same batch run. Per-batch stats (total/succeeded/failed/reason breakdown) are surfaced via API.

## Decision

Use **Spring Batch's built-in skip policy** with a **`SkipListener` that writes skipped records to `dead_letter_records`**, using `REQUIRES_NEW` transaction propagation so dead-letter writes survive chunk rollbacks.

## Failure Classification

The key engineering decision is which exception types skip vs. retry vs. fail the batch:

| Exception Class | Action | Rationale |
|---|---|---|
| `ValidationException` | **Skip + Dead-Letter** | Application-level validation failure; known, structured, recoverable by fixing the source data |
| `FlatFileParseException` | **Skip + Dead-Letter** | Malformed CSV row; can be corrected in source and reprocessed |
| `TransientDataAccessException` | **Retry (max 3×, exponential backoff starting 1s)** | Transient DB issue (connection pool exhaustion, lock timeout); likely self-healing |
| `DataIntegrityViolationException` | **No-skip (fail chunk)** | Schema violation not caught by application validation; indicates a code bug, not a data issue |
| Other unchecked exception | **Fail batch** | Unknown failure; escalate for investigation |

The skip limit for `ValidationException` is `Integer.MAX_VALUE` — the system never fails a batch due to validation volume alone. If 80% of a batch fails validation, metrics surface it immediately and operators investigate without blocking the valid 20%.

## Dead-Letter Schema Design

```sql
dead_letter_records (
    id               BIGSERIAL PRIMARY KEY,
    batch_id         VARCHAR(255)  -- links to batch_run_metadata for breakdown queries
    feed_type        VARCHAR(50)   -- accounts | transactions
    raw_record       TEXT          -- original unparsed line, always preserved
    failure_reason   VARCHAR(100)  -- machine-readable code: invalid_email, missing_field, etc.
    failure_field    VARCHAR(100)  -- which field failed, for operator triage
    failure_message  TEXT          -- human-readable explanation
    status           VARCHAR(30)   -- PENDING | REPROCESSED | PERMANENTLY_FAILED
    reprocessed_at   TIMESTAMP     -- when this record was reprocessed
    reprocess_batch_id VARCHAR(255) -- the batch that reprocessed it
)
```

**`raw_record TEXT`**: The original unparsed line is preserved before any transformation. This is critical — it means operators can fix the data issue and resubmit exactly what was received, without reconstructing it from the parsed (and potentially partially-transformed) fields.

**`failure_reason VARCHAR(100)`**: Machine-readable code, not a human message. Codes are defined in the `FailureReason` enum: `invalid_email`, `missing_field`, `duplicate_in_batch`, etc. This enables analytic queries: "how many invalid_email failures did we have last week?" without parsing text.

**`failure_field VARCHAR(100)`**: Pinpoints which field caused the failure. Combined with `failure_reason`, this tells operators exactly what to fix without reading the full error message.

**`REQUIRES_NEW` transaction propagation**: The `DeadLetterService.save()` method runs in a separate transaction from the batch chunk. This ensures dead-letter writes commit even when the surrounding chunk rolls back (e.g., on a transient DB error that triggers a retry). Without this, dead-letter records could be lost during retry rollbacks.

## Retry vs. Permanent Failure

**Validation failures are never auto-retried.** Retrying a record with an invalid email will always produce the same `ValidationException`. Auto-retry would consume retry budget for zero benefit.

**Transient failures use exponential backoff.** The retry sequence is: immediate, then 1s, then 2s (configurable via `bulkflow.batch.retry-backoff-ms`). After 3 failures, the chunk is committed with however many records succeeded; the transient-failed record is written to dead-letter.

**Manual reprocessing via API.** Dead-lettered records can be queried by batch_id, browsed by failure reason, and bulk-marked as reprocessed via `POST /api/dead-letter/{batchId}/reprocess`. The actual re-ingestion happens by re-uploading a corrected file — the reprocess API call marks records as `REPROCESSED` and links them to the new batch for audit trail continuity.

## Consequences

- Operators have a queryable, reprocessable dead-letter store — visibility is a first-class concern, not an afterthought.
- Valid records always proceed regardless of adjacent invalid records. A batch with 12 bad records in 10,000 succeeds for all 9,988 valid ones.
- Per-batch failure breakdown (reason code → count) is surfaced in the metrics API and printed as a formatted summary box in application logs at batch completion.
- The skip limit is set to `Integer.MAX_VALUE` for validation failures deliberately. A future guardrail could alert (but not fail) when skip rate exceeds a configurable threshold (e.g., 50%) — this is a monitoring concern, not a batch-failure concern.
