# mr.x — Final Repair Audit — 2026-09-24

## Scope
The complete uploaded source package was inspected before modification: Android source, Gradle configuration, manifest/resources, offline store, service routing, Supabase configuration/migration, verification scripts, build/bootstrap helpers, and project documentation.

## Repair release
### v55 — account-bound offline queue + request/media hardening
1. Bound every newly queued offline mutation to the authenticated user ID.
2. Prevented a second account on the same device from synchronizing another account's queued media/mutations.
3. Added a safe legacy adoption path only when an old ownerless queued mutation explicitly identifies the current account through its payload or URL; otherwise it remains unsynchronized rather than crossing account boundaries.
4. Replaced the previous plaintext offline user-ID cache with a Keystore-protected encrypted user-ID value.
5. Added a migration to the offline queue schema (`DB_VERSION = 3`) with `owner_user_id`.
6. Restricted native media playback to the app's configured public media storage path and normalized HTTPS port handling.
7. Added a 512 KiB maximum JSON request-body size to prevent accidental oversized API requests.
8. Removed the publishable key from the `Authorization: Bearer` header for unauthenticated requests; it remains in the required `apikey` header.
9. Completed the profile flow with explicit logout and bounded profile-field lengths.
10. Raised the client-side password minimum from 6 to 8 characters for new authentication attempts.
11. Updated the project release to **2.15.0 / versionCode 40**.
12. Updated the declared Gradle distribution target to **9.7.1**.

## Verification
- Security: PASS
- Hardening: PASS
- Offline behavior: PASS
- Regression: PASS
- Completeness: PASS
- Service isolation: PASS
- Multi-server routing: PASS
- Native-only: PASS
- Repair-specific static checks: PASS

## Build status
A real APK build remains **NOT VERIFIED** in this environment. JDK 21 is available, but the environment has no installed Android SDK, no system Gradle executable, and the uploaded project does not contain the official `gradle-wrapper.jar`. Therefore source/static checks are not represented as proof of a successful APK build or device runtime.

## External 2026 compatibility checks
The project is aligned against current official 2026 documentation: Android Gradle Plugin 9.4.1 is the current stable AGP API release, AGP 9.4 requires Gradle 9.6.0 or newer, Gradle 9.7.1 is the current Gradle release, and Kotlin 2.4.20 is the current Kotlin stable release line. Firebase Android documentation currently lists BoM 34.19.0. OWASP Mobile Application Security released MASWE 1.0.0 in August 2026. These sources were used as compatibility/security references; dependencies were not blindly upgraded where the project does not use them.

## Release
**mr.x 2.15.0 — versionCode 40**


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


### v59/v2.18.0 — post-v57 defensive repair
- Bound successful password-token authentication to the returned UUID immediately when Supabase includes the user object, reducing the window in which offline mutations can lack an owner binding.
- Cleared pending password-recovery state after normal login and logout, preventing stale recovery state from surviving an account transition.
- Hardened trusted public-media path validation against dot-segment and encoded traversal forms before native media access.
- Updated release metadata to **2.18.0 / versionCode 44**.
- No new runtime dependencies introduced.

### Verification
- Existing security/hardening/offline/regression/completeness/service-isolation/multi-server/native-only checks: PASS
- Repair-specific checks: PASS
- Official Android APK build/install/runtime: **NOT VERIFIED**; Android SDK/Build Tools and official Gradle wrapper JAR are absent in the execution environment.
