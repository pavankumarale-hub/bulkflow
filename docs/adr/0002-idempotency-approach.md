# ADR 0002 — Idempotency Approach: SHA-256 Row Hash as Conflict Key

**Status:** Accepted  
**Date:** 2024-01

## Context

Re-processing the same file (due to a retry, redeploy, or operator error) must not create duplicate records in the target table. Several approaches were evaluated:

### Option A: Natural Key Uniqueness Only
Use `account_id UNIQUE` as the sole conflict target. Simple, but has a critical ambiguity: if the same `account_id` appears in two different files with updated field values, we cannot distinguish "re-ingesting the same record" from "ingesting an updated version of the same account." The `DO NOTHING` semantic would silently discard legitimate updates.

### Option B: File Checksum + Row Offset
Dedup by `(file_sha256, line_number)`. A given row is idempotent as long as the source file is bit-for-bit identical. Fails if upstream re-generates the file with the same logical content but different bytes (different encoding, trailing whitespace, timestamp in header). Also requires storing the file checksum as a join key, adding complexity to every query.

### Option C: Application-Level Read-Before-Write
Before inserting each record, query for existence by natural key. If found, skip. Problems: N reads + N writes (double the round-trips), race conditions under concurrent batch runs against the same table (two jobs could both read "not found" and both insert), and this pattern serializes what could otherwise be parallelized.

### Option D: SHA-256 Hash of Record Content (chosen)
Compute a hash of `field1|field2|...|fieldN` (all mutable field values, null-safe, pipe-delimited). Store as `row_hash VARCHAR(64)`. Use `ON CONFLICT (row_hash) DO NOTHING`.

## Decision

Use a **SHA-256 hash of the record's natural key concatenated with all mutable field values**, stored as `row_hash`, with `ON CONFLICT (row_hash) DO NOTHING` on insert.

Hash input format:
```
account_id|email|first_name|last_name|status|date_of_birth|phone|credit_limit|currency
```
Null fields are normalized to empty string before hashing. All fields are post-normalization (lowercase email, uppercase status/currency) so the hash is stable across cosmetic variations.

## Rationale

**Deterministic and content-addressable.** The same logical record — regardless of which file carries it, which run processes it, or which operator retries it — produces the same hash. No external coordination needed.

**Zero read-before-write overhead.** Idempotency is enforced at the database `UNIQUE` constraint level, not application logic. Zero extra round-trips per record.

**Correct partial retry semantics.** If a 100k-record job fails after writing 60k records, a re-run processes all 100k but the first 60k are silently skipped by hash conflict. Only the remaining 40k are actually written. This is exactly the desired behavior.

**Decoupled from file identity.** The hash is computed from record content, not the file it came from. This is correct for ETL systems where the same data may arrive via different files (initial load, correction files, redeliveries).

## Tradeoffs Considered

**Hash collision probability.** SHA-256 has 2²⁵⁶ possible values. The birthday paradox collision probability at 10 billion records is approximately 10⁻⁵⁸ — effectively zero. Acceptable for any realistic data volume.

**Updated records.** If an account's email changes and the corrected record is re-ingested, the new hash differs from the old one → a new row is inserted, old row is not updated. This is by design for a load-once idempotent pipeline; update semantics would require a separate update path (upsert via `ON CONFLICT DO UPDATE`), which is a different problem with different consistency requirements.

**Hash computation cost.** SHA-256 at ~1μs/record on JVM. At 100k records: ~100ms. Negligible compared to I/O.

**Storage overhead.** 64 bytes (hex) per row. At 100M rows: ~6.4GB. Acceptable. The index on `row_hash` adds comparable overhead but is the core mechanism enabling idempotency.

## Consequences

- Every row carries a 64-char hex row_hash. This column is indexed and the primary conflict target.
- Re-running any batch against any file is safe by default — operators can retry without checking whether the data was previously loaded.
- Content-identical records from different sources hash to the same value → only one copy lands in the target table. This is correct for dedup scenarios but means BulkFlow does not support intentional duplicate ingestion (which would be unusual for an account/transaction feed).
- The hash function and field ordering must not change without a data migration — if the hash algorithm changes, all existing hashes become stale and re-runs would create duplicates.
