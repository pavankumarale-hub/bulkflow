# ADR 0001 — Batch Load Strategy: Batched INSERT with ON CONFLICT DO NOTHING

**Status:** Accepted  
**Date:** 2024-01

## Context

BulkFlow needs to write up to hundreds of thousands of records per job run efficiently to PostgreSQL. Three strategies were evaluated:

### Option A: PostgreSQL COPY Protocol
The fastest raw-throughput mechanism — the `COPY` command can load ~1M rows/min over a socket connection. However:
- Requires `SUPERUSER` or `pg_write_server_files` privilege in most managed database environments (RDS, Cloud SQL)
- Bypasses row-level constraints by default — a row that violates a `UNIQUE` constraint during COPY causes the entire COPY statement to fail, not just that row
- Doesn't support `ON CONFLICT` semantics natively — to achieve idempotency you need a staging table, adding a second DDL/DML round-trip
- Cannot be integrated into Spring Batch's chunk transaction boundary without a custom `ItemWriter` that reimplements skip/retry logic from scratch

### Option B: Staging-Table-then-MERGE
Write to a temporary staging table first, then `MERGE`/`INSERT ... SELECT` into the target.
- Supports conflict resolution and partial updates
- But adds a full second round-trip per batch (write staging → merge → clean up staging)
- Doubles transaction size and lock contention on the target table during the merge window
- Temporary tables in PostgreSQL are session-scoped; Spring Batch's concurrent step model would require careful session affinity
- More schema migration surface area (staging table needs to exist or be created per-run)

### Option C: Batched INSERT via JdbcBatchItemWriter (chosen)
Spring Batch's `JdbcBatchItemWriter` accumulates records per chunk (default 500) and executes a single multi-row INSERT per chunk via JDBC's `addBatch()`/`executeBatch()` API.

## Decision

Use **`JdbcBatchItemWriter` with `INSERT ... ON CONFLICT (row_hash) DO NOTHING`** at JDBC batch_size=500.

The SQL:
```sql
INSERT INTO accounts (account_id, email, ...)
VALUES (:accountId, :email, ...)
ON CONFLICT (row_hash) DO NOTHING
```

## Rationale

**Correctness over raw speed.** COPY bypasses constraint checks; batched INSERT guarantees every row is validated against schema constraints before commit.

**Idempotency at the write layer.** `ON CONFLICT (row_hash) DO NOTHING` makes re-runs safe by design — no extra application logic, no staging table, no read-before-write race condition.

**Spring Batch integration is the key lever.** `JdbcBatchItemWriter` participates in Spring Batch's chunk transaction, retry, and skip machinery natively. A COPY-based writer would require reimplementing that machinery: it cannot skip individual rows during a COPY because COPY is all-or-nothing at the statement level.

**Throughput is sufficient.** At batch_size=500 and a 5ms JDBC round-trip, batched INSERT achieves ~100k rows/min. For the target use case (bulk onboarding, not real-time streaming), this comfortably handles millions of records per hour. The engineering story is correctness and recoverability — not raw throughput.

**Operational simplicity.** No staging tables to create, maintain, clean up, or recover from partial failure. The target table is the only write target.

## Alternatives Rejected

| Option | Why rejected |
|--------|-------------|
| COPY | Permission requirements in managed DBs; incompatible with row-level skip; ON CONFLICT requires staging |
| Staging-table MERGE | Double round-trips; lock contention; schema surface area; session affinity requirements |
| Row-by-row INSERT | N+1 round-trips; 100k records would take minutes |

## Consequences

- Load throughput is ~100k rows/min (vs. COPY's ~1M). This is a deliberate trade-off: correctness and operational simplicity over raw speed.
- Row-level conflict detection is built into the write phase, reducing write-side error handling code.
- If the `row_hash` index is dropped accidentally, re-runs will create duplicates. The `UNIQUE` constraint on `row_hash` in the schema (V1 migration) is the safety net.
- Future optimization path: if throughput requirements increase significantly, introduce parallel step partitioning (partition by account_id hash range) before switching to COPY — partitioning gives ~4x throughput gains with the same correctness guarantees.
