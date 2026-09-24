# Development status

Baseline: Social Network Android Native v16

## Protected baseline

- Native Android UI; no WebView.
- Supabase email/password authentication.
- Encrypted session storage using Android Keystore + AES/GCM.
- Auth refresh and authenticated retry.
- User identity verification through Supabase Auth.
- RLS-backed posts, likes, comments, profiles, follows, notifications, messages, and stories.
- User-scoped Storage paths.
- Media size/response limits and orphan cleanup.
- Feed pagination and request de-duplication.
- Instagram-inspired visual presentation only.

These are protected. Do not rework them unless regression evidence exists.

## Next development targets

1. Extract repositories from `MainActivity` without behavior changes.
2. Add lifecycle-safe state holders/ViewModels.
3. Add Realtime subscriptions for messages/notifications/feed interactions.
4. Add FCM push notification pipeline.
5. Add proper media preview components and upload progress.
6. Add search, block/report, privacy controls, and account settings.
7. Add offline cache/sync.
8. Add instrumentation/end-to-end tests.
9. Build and test the APK on a real Android toolchain.

## v26 hardening applied

- Lifecycle UI callbacks are re-checked at execution time, and pending main-thread callbacks are removed during activity destruction.
- Pending camera output is cleaned up when the activity is actually finishing.
- Document picker requests persistable read permission; camera capture carries the output URI through `ClipData` for stronger OEM compatibility.
- Media MIME types are mapped to matching storage extensions; unsupported media types are rejected instead of being mislabeled.
- Failed/partial uploads attempt synchronous orphan cleanup, including a refresh-and-retry path after HTTP 401.
- Trusted media URLs now enforce the configured HTTPS port as well as the configured host.
- Version bumped to 2.2.1 (versionCode 14).

## Known verification limitation

The development environment used to prepare this source package does not currently provide a complete Android SDK/Gradle toolchain. Therefore source/static verification does not equal a successful APK build.

## v16 verification note

The v16 source was statically repaired and re-verified. Two compile-blocking defects were removed: a duplicate `onDestroy()` referencing a nonexistent executor, and a missing `readLimited()` helper used by the native image viewer. The media viewer was also hardened with HTTPS/Supabase-host validation, bounded image reads, connection cleanup, and video playback cleanup on dialog dismissal.

The remaining build blocker is environmental: this package does not contain the official Gradle Wrapper JAR, and the current verification environment does not have a system Gradle installation or Android SDK. No APK build is claimed until `:app:assembleDebug` completes successfully on a real Android toolchain.


## v28 resilience hardening
- Added bounded retry/backoff for idempotent GET/DELETE requests on 408/429/500/502/503/504.
- Honors numeric Retry-After when supplied, capped to 5 seconds.
- POST/PATCH are intentionally not retried generically to avoid duplicate mutations.
- Version bumped to 2.2.3 (versionCode 16).


## v31 Hardening pass
- Added read-replica transient/network fallback to the configured write/primary endpoint for idempotent REST reads.
- Fixed media URL trust to validate against the configured Storage endpoint, not only the primary database endpoint.
- Added message send in-flight guard to prevent rapid duplicate sends.
- Added profile update in-flight guard.
- Sanitized/limited people-search input before constructing PostgREST ilike filters.
- Added regression checks for all of the above.
- Actual Android build/runtime remains unproven until Android SDK + compatible Gradle toolchain are available.
## High-load client hardening — 2026-09-24
- Shared background execution is now a bounded 6-worker pool with a 256-task queue.
- This increases safe network/media concurrency without allowing unbounded thread creation or an unbounded work queue.
- A bounded `AbortPolicy` provides hard back-pressure; `MainActivity.submitIo()` catches rejection and reports saturation to the UI instead of executing work on the UI thread or silently dropping it.
- This is client-side pressure control; actual multi-user capacity still depends on the Supabase/database/API/Storage infrastructure and requires a real load test.



## v38 — Backend high-load hardening
- Added `supabase/migrations/20260924_090300_high_load_indexes.sql` with defensive PostgreSQL indexes for the app hot paths: posts, stories, profiles, follows, notifications, conversations, messages, likes, and comments.
- Added `tools/loadtest-read.py` for HTTPS read-only concurrency testing with throughput and p50/p95/p99/error reporting.
- Added `HIGH_LOAD_BACKEND.md` documenting the deployment/measurement gate.
- Backend capacity is not claimed as proven because this repository does not contain the live Supabase database/schema or production connection.

### v39 — high-load backpressure + staged load test
- Changed the shared Android IO executor from `CallerRunsPolicy` to `AbortPolicy`; a saturated queue can no longer execute background work on the UI/caller thread.
- Kept the bounded 6-worker / 256-task policy as client-side backpressure.
- Extended `tools/loadtest-read.py` with bounded batches and optional concurrency ramps (`--ramp 25,50,100,250`) so large tests do not enqueue an unbounded number of Future objects.
- Bumped Android version to 2.9.0 / versionCode 23.
- This still does not prove production backend capacity; the load test must target a real staging/production read endpoint.


## 2026-09-24 — 100% Free User Access
- Core mr.x user access is intended to remain 100% free.
- No mandatory subscription, trial expiry, paid unlock, in-app purchase, or payment gate.
- No Google Play Billing dependency or billing permission.
- Realtime subscriptions are technical event subscriptions, not paid subscriptions.
- `tools/verify-free.py` protects these source-level invariants.

## v54 — Executor rejection state + offline sync scheduling repair
- Fixed bounded-executor saturation state locks across operations that use in-flight guards.
- Offline sync now uses a shared tagged retry callback that can be cancelled by normal sync scheduling.
- Rejected offline sync submissions release `syncInFlight` immediately.
- Version 2.15.0 / versionCode 40.
- Static verification passes; Android APK build/install/runtime remains unverified without the official Android/Gradle toolchain.


## Repair pass 2026-09-24 — v2.16.0 / versionCode 41
- Offline queue reads and retry scheduling are scoped to the authenticated account.
- Legacy unbound queue rows are considered for adoption only after ownership fields are matched to the active account.
- Gradle 9.7.1 distribution checksum and wrapper validation settings are pinned.
- No new feature dependencies were introduced.

## v57 — Account-scoped offline response cache repair — 2.17.0 / versionCode 42
- Re-audited the v56 source before modification.
- Bound offline GET response cache entries to the authenticated user ID, preventing cached authenticated data from crossing account boundaries on the same device.
- Added SQLite cache schema migration to v4 with `owner_user_id`; legacy cache rows are deliberately invalidated because ownership cannot be proven retroactively.
- Cleared local response cache during logout.
- Added regression gates for cache ownership, migration, and logout cleanup.
- Removed a duplicate `errorMessage` method declaration that was a Java compile blocker in v56.
- Updated release metadata to **2.17.0 / versionCode 42**.
- No new feature dependencies were introduced.

### Verification
- Security: PASS
- Hardening: PASS
- Offline: PASS
- Regression: PASS
- Completeness: PASS
- Service isolation: PASS
- Multi-server: PASS
- Native-only: PASS
- Cache account isolation: PASS
- Python verifier compilation: PASS
- Official Android APK build/install/runtime: **NOT VERIFIED** because the execution environment lacks the Android SDK/Build Tools and the official Gradle wrapper JAR.

