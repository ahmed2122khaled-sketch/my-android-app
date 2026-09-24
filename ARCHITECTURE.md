# Android architecture — continuous development

This project is intentionally prepared for continuous feature development. Existing fixed behavior is treated as a protected baseline; new work should extend feature boundaries instead of rewriting stable fixes.

## Boundaries

```text
UI / Activity
    |
    +-- Feature screens/actions
    |
    +-- Core constants / feature flags
    |
    +-- Network + Auth + Storage (current implementation)
    |
    +-- Supabase REST / Storage
    |
    +-- PostgreSQL + RLS
```

## Rules for every future change

1. Start from the latest version only.
2. Do not re-fix a closed issue unless a new test or runtime evidence shows it regressed.
3. One feature/change should have one clear boundary and one verification target.
4. Never put service-role credentials in Android.
5. Keep database authorization in RLS; client checks are UX/defense-in-depth only.
6. Any Storage upload must be user-scoped and cleaned up when its database transaction fails.
7. Network operations stay off the main thread.
8. UI updates return to the main thread.
9. New features should be guarded by `FeatureFlags` until their end-to-end verification is complete.
10. Increment `versionCode` for every packaged build; update `versionName` for user-visible releases.

## Planned extraction path

`MainActivity` is currently functional but large. Future work should extract, in this order:

- `AuthRepository` — session/authentication only.
- `SupabaseClient` — HTTP, auth headers, retries, response limits.
- `MediaRepository` — picker-independent Storage operations.
- `FeedRepository` — posts, likes, comments, pagination.
- `SocialRepository` — profiles, follows, notifications.
- `MessagingRepository` — conversations/messages/realtime.
- Feature Activities/Fragments/ViewModels or Compose screens.

Do not perform a mechanical split without compile + functional verification after each extraction.


## Multi-server service routing (v2.3.0)
The Android client now supports separate service origins without changing feature code:
- `SUPABASE_DATA_READ_URL`: read-only REST GET/HEAD endpoint (Read Replica or API load balancer).
- `SUPABASE_DATA_WRITE_URL`: primary REST endpoint for INSERT/PATCH/DELETE/RPC mutations.
- `SUPABASE_AUTH_URL`: Auth endpoint.
- `SUPABASE_STORAGE_URL`: Storage endpoint.
- `SUPABASE_URL`: primary/fallback endpoint and required base configuration.

Routing is conservative: only REST GET/HEAD requests may leave the primary data endpoint. Auth, Storage, and mutations remain on their dedicated/primary endpoints. This prevents writes from accidentally reaching a read replica and avoids splitting Auth/Storage across unsupported replica endpoints.

Supabase documents that Read Replicas accept GET requests for the Data API and that its API load balancer can geo-route eligible GET traffic while sending writes to the primary. Auth, Storage, and Realtime are not read-replica services.
