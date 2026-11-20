# ReconFlow - Spring Batch Reconciliation Backend

A Spring Boot backend for financial reconciliation of ledger transactions against gateway settlement records.

## Overview

ReconFlow processes CSV gateway statements and bank ledger data, matches transactions, and produces:
- **GatewaySettlement** records (matched or missing in ledger)
- **ExceptionQueue** entries (amount mismatches, missing references)
- **ChainAMatch / ChainBMatch** pairs (ledger-bank correspondence)

The system uses **state machines** with immutable enum statuses, **Redis distributed locks**, and **Testcontainers** (Postgres/Redis/LocalStack) for all integration testing.

## Recent hardening: batch-output idempotency

When a reconciliation job is retried (e.g., after a partial failure), the original code could create duplicate rows in the output tables. This change adds deterministic dedup logic so retries never insert second-class rows.

### What was deduped

| Table / Path | Dedup key |
|---|---|
| `gateway_settlements` | `(uploaded_file_id, gateway_transaction_id)` |
| `exception_queue` | `(reconciliation_run_id, match_id, exception_type)` |
| `chain_a_matches` | `(reconciliation_run_id, ledger_transaction_id)` |
| `chain_b_matches` | `(reconciliation_run_id, gateway_settlement_id)` |
| Exception matchIds | Deterministic UUID from `runId + "∥" + key` so retries always resolve to the same exception |

### How it works

- **Repositories** expose new query methods (`findByUploadedFileIdAndGatewayTransactionIdIn`, `existsByReconciliationRunIdAndMatchIdAndExceptionType`, etc.)
- **ReconciliationWriter** checks existing rows before `saveAll`; skips duplicates
- **CsvInMemoryLoader.afterStep** uses the persisted settlement id as the exception `matchId`, so retry always reuses the same key
- **ChainBTasklet** skips settlements already matched in the current run; exception writes are guarded by the same exists check
- All logic is purely additive — first-run behaviour is unchanged because no rows exist yet to filter

## Prerequisites

- Docker & Docker Compose (for Testcontainers)
- Java 21+ (pom targets 21, runs on 25)
- Maven 3.9+

## Running tests

```bash
# Unit / batch-package tests (no Docker)
cd backend
mvn test -Dtest='ReconciliationWriterTest,ChainBTaskletTest,CsvInMemoryLoaderTest'

# Full suite (starts Postgres/Redis/LocalStack containers)
mvn test
```

The full test suite runs ~104 tests across unit, batch-package, and E2E groups. All pass with `BUILD SUCCESS`.

## Architecture snapshot

- **Enums** replace String status fields; `transitionTo()` guards enforce legal moves
- **@Version** optimistic locking on `UploadedFile` / `ReconciliationRun`
- **Redis SETNX** distributed lock (TTL configurable, default 1800s) in `SqsJobListenerService`
- **Stale-PENDING sweeper** (`StaleLedgerSweeperTask`) marks rows STALE after 7 days
- **DLQ** persistence via `DlqFailureService` (file→FAILED, dedup by matchId+exceptionType)
- **Idempotency TTL** (`reconflow.upload.idempotency-ttl-seconds`, default 300s) + rollback cleanup
- **Orphaned QUEUED recovery** task requeues abandoned runs

## License

MIT