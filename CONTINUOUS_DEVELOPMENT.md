# Continuous development workflow

## Before coding

- Use the latest packaged version as the only baseline.
- Read `ARCHITECTURE.md` and `DEVELOPMENT_STATUS.md`.
- Check the closed-fixes list before touching security/session/storage code.

## During coding

- Make the smallest coherent change that delivers the feature.
- Add or update a verification rule for every bug fixed.
- Avoid duplicate implementations of auth, HTTP, Storage, or session logic.
- Do not replace working native code with WebView or mock data.

## Verification gate

Run, in order:

```text
1. Static project verification
2. Java/Kotlin syntax/compile check when toolchain is available
3. Debug APK build
4. Install with ADB
5. Smoke test: launch -> login -> feed -> post -> media -> logout/login
6. Feature-specific end-to-end test
7. Regression test for the changed boundary
```

A static pass is not a runtime success claim.

## Versioning

- Patch: internal fixes with no new user-facing capability.
- Minor: new user-facing feature.
- Major: architecture/API contract change.

Never reuse a versionCode.
