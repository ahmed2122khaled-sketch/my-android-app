# Social Network — Android Native Only

This deliverable is an **Android application only**. The user-facing product is native Android UI; it does not use WebView, HTML pages, Next.js, Chrome, or a browser as an application shell.

## Runtime architecture

`Native Android UI -> HTTPS Supabase Auth/PostgREST/Storage/Realtime -> PostgreSQL`

Supabase is a backend service only. It is not rendered as a web interface inside the app.

Media is opened with native Android components (`ImageView` / `VideoView`) inside the app. No browser intent is used for normal post media viewing.

## Configuration

Copy `supabase.properties.example` to `supabase.properties` and set:

- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

Never put a Supabase service-role key in the APK.

## Native-only rules

- No WebView.
- No `loadUrl()`.
- No JavaScript bridge.
- No browser/Chrome as an application shell.
- No website frontend dependency at runtime.
- Native Android permissions and components only.

## Build

From this `android/` directory:

```bash
./gradlew :app:assembleDebug
```

A real APK build still requires a complete Android SDK toolchain and Gradle. This source package currently does not include the official `gradle-wrapper.jar`; the included `gradlew`/`gradlew.bat` are fallback launchers that use a system Gradle installation. Therefore this package must not be described as a verified APK build until `:app:assembleDebug` succeeds on a real Android toolchain.

## Current hardening

Current source version: **2.18.0 (versionCode 44)**. The latest hardening covers lifecycle-safe UI callbacks, document/camera URI grants, strict MIME-to-extension mapping, partial-upload cleanup, and trusted media URL port validation.

These changes have passed static/native/security verification. They have **not** been represented as a successful APK build because this environment still lacks the official Gradle Wrapper JAR, system Gradle, and Android SDK.

## Reproducible toolchain bootstrap

The repository now includes reproducible bootstrap/build helpers under `tools/`:

- Linux: `./tools/bootstrap-linux.sh` then `./tools/build-linux.sh`
- Windows PowerShell: `powershell -ExecutionPolicy Bypass -File .\tools\bootstrap-windows.ps1`
- Diagnostics: `./tools/doctor.sh`

The bootstrap uses the official Gradle 9.7.1 distribution and Android command-line tools, then installs `platform-tools`, `platforms;android-36`, and `build-tools;36.0.0`. The project launcher prefers the bootstrapped local Gradle installation when present.

These scripts make the build reproducible, but they do not falsely mark this source package as runtime-verified. APK build/install/runtime verification remains pending until a machine with network access to the official tool repositories and an Android device or emulator is used.

## 100% Free User Access

mr.x is designed to remain **100% free for users**. The core application does not implement mandatory subscriptions, trial expiry, paid unlocks, in-app purchases, or payment-gated access. No Google Play Billing dependency or billing permission is included.

Infrastructure can still have operating costs for the project owner; those costs do not create a payment requirement for ordinary mr.x users. Technical Supabase Realtime subscriptions are networking functionality and are not paid user subscriptions.

## Copyright and licensing

**Copyright © 2026 mr.x. All rights reserved.**

Original mr.x source and project materials are source-available and are not licensed for redistribution by default. Third-party components and services remain governed by their own licenses and terms. See `COPYRIGHT.md`, `LICENSE.md`, and `THIRD_PARTY_NOTICES.md`.

The application also exposes the same legal notice from the top-right information area of the native UI.
