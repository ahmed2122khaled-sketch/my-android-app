# mr.x Multi-Server Routing v2.4.0

The Android client supports service-specific HTTPS origins while keeping the existing Supabase project as the fallback.

## Roles

| Variable | Role | Allowed traffic |
|---|---|---|
| `SUPABASE_URL` | Primary/fallback | all services when overrides are empty |
| `SUPABASE_DATA_READ_URL` | Read server / Supabase Read Replica or API Load Balancer | REST `GET` / `HEAD` only |
| `SUPABASE_DATA_WRITE_URL` | Primary data server | REST mutations and RPC mutations |
| `SUPABASE_AUTH_URL` | Authentication server | `/auth/*` |
| `SUPABASE_STORAGE_URL` | Media/storage server | `/storage/*` |

All values must be HTTPS origins. The APK only accepts requests to configured service origins.

## Recommended production topology

1. **Primary Supabase project** — authoritative Postgres + writes.
2. **Read Replica #1** — close to the largest user region.
3. **Read Replica #2** — another major user region, if traffic justifies it.
4. **Supabase API Load Balancer** — preferred `SUPABASE_DATA_READ_URL` when available; it can geo-route eligible GET requests across the primary and read replicas.
5. **Supabase Storage CDN** — media delivery is already globally distributed; `SUPABASE_STORAGE_URL` remains the Storage API origin.
6. **Auth** — remains on the primary Auth service; it must not be sent to a database read replica.

Supabase documents that Read Replicas are read-only, while its API load balancer can geo-route eligible GET traffic and send non-GET data requests to the primary. Auth, Storage, and Realtime are not read-replica services.

## Example configuration

```properties
SUPABASE_URL=https://PRIMARY_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_KEY
SUPABASE_DATA_READ_URL=https://READ_OR_LOAD_BALANCER_ENDPOINT
SUPABASE_DATA_WRITE_URL=https://PRIMARY_PROJECT.supabase.co
SUPABASE_AUTH_URL=https://PRIMARY_PROJECT.supabase.co
SUPABASE_STORAGE_URL=https://PRIMARY_PROJECT.supabase.co
```

Do not put service-role/secret keys in the APK.

## Important

Adding these variables does not create infrastructure by itself. The actual Read Replicas/load balancer must first exist in the Supabase infrastructure. If the optional variables are left blank, mr.x automatically uses `SUPABASE_URL`, preserving the previous single-server behavior.


## v2.4.0 implementation note

The routing layer is now applied centrally inside `request()`, so authenticated REST calls cannot silently bypass the configured read/write/auth service routing. Storage uploads/deletes use `SUPABASE_STORAGE_URL` directly because they use streaming HTTP; media downloads keep their existing validated media URL path.

The client does not create servers. The configured endpoints must point to real infrastructure before multi-server acceleration exists at runtime.
