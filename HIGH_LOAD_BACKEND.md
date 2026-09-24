# mr.x Backend High-Load Hardening

## Implemented in this version

- Added a PostgreSQL/Supabase migration with indexes matching the native client's hot read/write paths.
- Indexes cover feed ordering, story expiry/order, follow lookups, notification feeds, conversations, message history, likes, comments, and profile ordering.
- Migration is defensive: it checks that tables/columns exist before creating each index.
- Added a **read-only** load-test utility at `tools/loadtest-read.py`.

## Important limitation

The Android repository does not contain the actual Supabase database schema or a live database connection. Therefore the migration has been added to the project, but it has **not** been truthfully reported as applied to production.

The load test also requires a real HTTPS staging/production REST endpoint and credentials. It refuses non-HTTPS and non-read endpoints.

## Recommended measurement gates

Run the same read-only scenario at increasing concurrency and record throughput, error rate, p50, p95, p99, database CPU, memory, connection usage, and storage/network saturation. Do not increase traffic after an error-rate or latency SLO breach.

The client-side changes in v37 and these backend changes are complementary: client queues protect individual devices; database indexes reduce query cost; actual capacity still depends on the deployed Supabase/PostgreSQL/Storage infrastructure.

## v40 local capacity harness

Added `tools/loadtest/local_backend.py`, a read-only local REST harness backed by SQLite with WAL and the same hot-path indexes used by the migration. It exists only to validate the load generator and service-layer behavior when a real Supabase staging endpoint is unavailable.

Observed local run (400 requests per stage, 10/25/50/100 concurrency): 0 errors at every stage. Throughput was approximately 75/88/107/271 req/s respectively. Latency tails increased materially under concurrency in this constrained environment, so these figures are **not production capacity numbers**.

The first run exposed a global SQLite read lock in the harness; it was removed and WAL/busy-timeout enabled. This is a harness-level root-cause fix, not a claim about Supabase.
