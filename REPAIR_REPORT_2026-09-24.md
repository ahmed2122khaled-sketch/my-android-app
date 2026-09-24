# mr.x Repair Report — 2026-09-24

## Actual code repairs

1. Removed the duplicate `MainActivity.onNewIntent(Intent)` declaration. This was a real Java compile blocker.
2. Fixed network detection for Android API 21–22 by using the legacy `NetworkInfo` path below API 23 and modern `NetworkCapabilities` on API 23+.
3. Removed the manifest portrait lock so Android 16 large-screen behavior can resize/adapt the native activity instead of forcing a fixed orientation.
4. Hardened multi-server routing/fallback logic to use exact configured-origin parsing instead of URL `startsWith()` prefix matching.
5. Changed Offline mutation queue overflow behavior: it now rejects a new mutation instead of silently deleting an older user mutation.
6. Reclaims expired cache entries immediately after cache writes.
7. Bumped app version to `2.13.1` / versionCode `28`.
8. Added `tools/verify-repair.py` to regression-test the repaired conditions.

## Verification

PASS:
- repair-specific static verification
- offline verification
- regression/hardening verification
- security verification
- completeness verification
- high-load hardening verification
- multi-server verification
- service-isolation verification
- native-only verification
- Java brace/parenthesis balance
- ZIP/source integrity checks

BLOCKED:
- Real Gradle APK build/install/run in this execution environment because the Android SDK, Build Tools, adb, and Gradle wrapper JAR are not present.

This report deliberately does not claim an APK build or device runtime success.


## v45 compatibility repair
- Fixed API 23 connectivity detection so a working connection is not rejected solely because `NET_CAPABILITY_VALIDATED` is not reliable there.
- Added pre-API-24 `registerNetworkCallback()` monitoring with a fresh connectivity check before scheduling synchronization.
- Version bumped to 2.13.2 / versionCode 29.


## v46 — Runtime correctness repair
- Removed hard-coded story usernames from the feed strip; story identities are now loaded from live `stories` + `profiles` data.
- Fixed ConnectivityManager callback cleanup for API 21–23, matching the registration path and preventing Activity lifecycle leaks.
- Bumped app version to 2.14.0 / versionCode 30.
- Existing v45 API23 connectivity and offline queue fixes were retained unchanged.


## v47 — Offline Mutation Ambiguity Repair

- Fixed a duplicate-mutation risk in offline mode: POST/PATCH/DELETE operations are now queued only when network unavailability is known **before** the request starts.
- Removed post-failure requeueing for online write attempts, because a server may already have accepted the mutation before the client loses the response.
- Removed the same duplicate-risk fallback for online post/story media uploads; explicit preflight-offline branches remain available.
- Added regression checks preventing the unsafe requeue pattern from returning.
- Version: 2.14.1 / versionCode 31.

Static verification: PASS. APK build/run remains unverified until the Android/Gradle toolchain is available.


## v48 — Executor saturation + verification integrity repair
- Added `MainActivity.submitIo()` around all bounded executor submissions.
- `RejectedExecutionException` is now caught and reported instead of escaping UI callbacks and crashing the Activity.
- No work is executed on the UI thread when the bounded IO queue is full.
- Restored a comprehensive `tools/verify-project.py` because the v46 package contained only a subset of the historical verifier scripts.
- Updated version to 2.14.2 / versionCode 32.
- Static verification passed; APK build/runtime remains blocked until the official Android toolchain is available.


## v49 executor rejection/lifecycle repair — 2026-09-24

- Fixed remaining direct `ExecutorService.submit()` usage in the native media preview path.
- Media preview now uses the same bounded rejection handling as other background work while retaining `Future` cancellation on dialog dismissal.
- This closes the remaining `RejectedExecutionException` path when the bounded I/O queue is saturated.
- Existing bounded executor policy remains unchanged: 6 workers / 256 queued tasks; no unbounded thread creation and no UI-thread fallback.
- Version: 2.14.3 / versionCode 33.
- Static regression gates passed.
- Real APK build/install/run remains unverified until Android/Gradle toolchain is present.


## v50 — Android 21/22 Keystore compatibility repair
- Found mismatch between `minSdk=21` and direct use of API-23 `KeyGenParameterSpec`.
- Added API-gated AES Keystore path for API 23+.
- Added API 21/22 RSA-in-Keystore wrapping of an AES-128 session key using `KeyPairGeneratorSpec`, with the wrapped key stored in private preferences.
- Updated version to 2.14.4 / versionCode 34.
- Updated repair verifier to match the actual version and verify the legacy fallback path.
- Static validation passed after the repair; clean APK build/install/runtime still requires the Android/Gradle toolchain.


## v51 — Full Pass: Recovery Callback Security Hardening

- Re-audited the complete v50 source tree before changing code.
- Password-recovery callback now requires a cryptographically random 256-bit one-time state.
- State is encrypted at rest, expires after 15 minutes, and uses constant-time comparison.
- State is cleared after a valid callback; invalid attempts do not clear a legitimate pending recovery state.
- Static verifiers were synchronized with the actual release and now verify the recovery-state protection.
- No WebView, cleartext networking, service-role key, unsafe endpoint routing, or executor bypass was introduced.
- Custom URI interception remains a platform limitation; full interception resistance requires a configured HTTPS App Link domain, which was not invented without a real backend domain.
- Version: 2.14.6 / versionCode 36.


## v52.1 — 100% Free User Access Policy

- Version: **2.14.7 / versionCode 37**.
- Confirmed no Google Play Billing dependency or Android billing permission.
- Confirmed no paid-feature/purchase gate in application source.
- Added `FREE_POLICY.md`.
- Added `tools/verify-free.py` to protect the free-user invariant.
- Realtime subscriptions remain technical event subscriptions, not paid subscriptions.


## v53 — Final Security/Runtime Audit Pass — 2.14.8 / 38

- Re-audited the complete source tree after the free-access change.
- Disabled automatic HTTP redirects for authenticated API requests and direct Storage uploads. This prevents bearer credentials/API keys from being carried automatically to a redirected origin.
- Reused centralized connection timeout constants for authenticated requests instead of duplicating timeout literals.
- Expanded the free-access static gate to detect trial, paywall, payment-required, and subscription-required gates in application source.
- No new permission, WebView, cleartext transport, payment SDK, or server-secret path was introduced.
- Version: 2.14.8 / versionCode 38.

### Remaining platform limitation
Password recovery still uses the configured custom `mrx://` callback because no real HTTPS App Link domain/signing association was provided. Android documents verified App Links as the stronger interception-resistant mechanism; replacing the custom scheme requires the actual production domain and `assetlinks.json`, so no placeholder domain is introduced.


## v54 — Executor Rejection State + Offline Sync Scheduling Repair

- Fixed a state-lock bug caused by bounded executor saturation: operations that set `*InFlight` before submission now reset their state when `submitIo()` rejects the task. This covers feed loading, profile updates, people search, notification read batching, message sending, story publishing, post publishing, password recovery, and offline synchronization.
- Fixed offline-sync retry scheduling so retry callbacks use the same tagged handler queue as normal sync scheduling; `scheduleSync()` can now cancel both normal and delayed retry callbacks.
- If offline synchronization itself is rejected because the bounded executor is saturated, `syncInFlight` is immediately released instead of becoming permanently stuck.
- Added regression gates for these conditions so future hardening passes cannot silently reintroduce the lock-up behavior.
- Version: **2.15.0 / versionCode 40**.

### Verification
- Static source structure: PASS
- Python helper compilation: PASS
- XML parsing: PASS
- Existing security/offline/native/multiserver hardening gates: PASS where executable in this environment
- Official Android APK build/install/runtime: **NOT VERIFIED** because this environment still lacks the Gradle executable/wrapper JAR and Android SDK toolchain.


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

