# mr.x — Online + Offline

## What was added

- Private SQLite offline store inside the Android app.
- Cache of successful authenticated GET responses for feed, profiles, people, messages, notifications, stories and other read endpoints.
- Persistent mutation queue for POST/PATCH/DELETE REST operations that are intentionally safe to replay through the normal service-routing layer.
- Automatic network monitoring with Android `ConnectivityManager`.
- Automatic synchronization when connectivity returns, in FIFO order.
- Access/refresh tokens remain in Android Keystore-backed encrypted preferences; they are never copied into the offline SQLite database.
- Offline text posts, comments, messages, profile changes and other REST mutations can be saved locally and synchronized later.
- Offline photo/video posts and stories are copied into app-private `filesDir/offline-media/`, queued, uploaded to Storage when online, then committed to the database. Local temporary media is removed after successful synchronization.
- Failed queued 4xx operations that cannot be replayed are removed instead of retrying forever; transport/authentication failures keep the operation queued.
- Cached reads are used when the device has no validated Internet connection.

## Deliberate limitations

- A user must have logged in at least once and have a valid cached session/user ID before authenticated offline actions can work.
- The first-ever feed/profile/messages load cannot be shown offline because there is no prior server snapshot.
- Authentication, token refresh, and direct Storage upload are not blindly queued; they require connectivity unless a media operation has already been converted into the dedicated offline-media queue.
- RPC operations that require a server-generated response (for example, creating a new conversation) remain online-only rather than inventing a local server ID.

## Synchronization safety

The queue uses the current Keystore-backed access token when replaying operations. It does not persist bearer tokens in the queue. Requests are routed through the existing service-isolation/multi-server layer, and existing retry rules remain unchanged.

## Verification

Run:

```bash
python3 verify-offline.py
python3 verify-completeness.py
python3 verify-native-only.py
python3 verify-security.py
python3 verify-hardening.py
python3 verify-multiserver.py
python3 verify-service-isolation.py
python3 verify-regression.py
```

A real APK build/install/runtime test is still required on a machine with Android SDK Platform 36, Build Tools 36.0.0, ADB and the Gradle 9.7.1 wrapper/toolchain. This environment currently does not have those build tools available, so no APK success claim is made here.
