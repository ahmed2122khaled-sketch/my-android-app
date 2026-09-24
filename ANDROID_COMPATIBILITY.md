# mr.x Android Compatibility and Cloud + Local Data

## Android compatibility
- Minimum supported API: 21 (Android 5.0 / Lollipop).
- Compile/target API: 36 (Android 16).
- No `maxSdkVersion` is declared, so newer Android releases remain eligible.
- APIs introduced after API 21 are guarded where required.
- Network callbacks use API 24+ and startup/connectivity checks cover older supported versions.
- Camera runtime permission is requested only on API 23+.
- System-bar and edge-to-edge code has API-specific branches.

This is broad platform compatibility, not a guarantee for every OEM firmware; representative real-device testing is still required.

## Cloud database
Supabase/PostgreSQL is the remote source of truth for authenticated application data. The APK does not contain service-role credentials.

## Local database
`OfflineStore` is a private SQLite database on the device. It stores cached responses, pending offline mutations, synchronization state, expiry information, and retry counters. Authentication tokens stay outside SQLite in the Android Keystore-backed secret store.

## Synchronization
The app can read cached/local data while offline and persist supported mutations for later synchronization. Network restoration drains the queue with bounded retries. Offline media is stored in the app-private directory until upload succeeds.

This follows Android's documented offline-first pattern: a local data source plus a network data source, with synchronization and conflict handling in the data layer.


### v50 runtime compatibility repair
- The project keeps `minSdk = 21`.
- Android Keystore AES generation with `KeyGenParameterSpec` is used only on API 23+.
- Android 5.0/5.1 (API 21/22) use an Android Keystore RSA key to wrap a randomly generated AES-128 session-encryption key. The wrapped key is stored in private preferences; the RSA private key remains in Android Keystore.
- This removes the previous API-level mismatch in the session-token protection path.
- Real-device validation on API 21/22 remains required before claiming runtime compatibility.
