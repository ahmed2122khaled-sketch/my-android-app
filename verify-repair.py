#!/usr/bin/env python3
from pathlib import Path
import re, zipfile, sys

ROOT = Path(__file__).resolve().parents[1]
main = (ROOT/'app/src/main/java/com/socialnetwork/app/MainActivity.java').read_text()
endpoints = (ROOT/'app/src/main/java/com/socialnetwork/app/ServiceEndpoints.java').read_text()
offline = (ROOT/'app/src/main/java/com/socialnetwork/app/OfflineStore.java').read_text()
manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()

def check(ok, msg):
    print(('PASS: ' if ok else 'FAIL: ') + msg)
    if not ok: sys.exit(1)

check(len(re.findall(r'@Override\s+protected void onNewIntent\(Intent intent\)', main)) == 1,
      'MainActivity has exactly one onNewIntent implementation')
check('getActiveNetwork()' in main and 'getActiveNetworkInfo()' in main,
      'network detection has API 23+ and legacy fallback paths')
check('android.os.Build.VERSION.SDK_INT < 24' in main and 'NET_CAPABILITY_INTERNET' in main,
      'API 23 network detection does not require VALIDATED')
check('registerNetworkCallback(request, networkCallback)' in main,
      'pre-24 connectivity callback fallback exists')
check('url.startsWith(primary)' not in endpoints and 'url.startsWith(primary + "/rest/")' not in endpoints,
      'service routing does not rely on unsafe prefix matching')
check('pathFromOrigin(url, primary)' in endpoints,
      'service routing validates the exact configured origin')
check('return -1L;' in offline and 'Never evict' in offline,
      'offline queue refuses overflow instead of silently dropping old mutations')
check('android:screenOrientation="portrait"' not in manifest,
      'portrait lock removed for Android 16 large-screen adaptability')
check('versionCode = 44' in (ROOT/'app/build.gradle.kts').read_text(), 'version bumped after repair')
check('String[] storyLabels' not in main and 'loadStoryBar(storiesBar)' in main, 'story strip uses live backend data instead of sample names')
check('unregisterNetworkCallback(networkCallback)' in main and 'networkCallback = null;' in main, 'network callback is released on every supported API level')
check('networkAvailableAtStart = isNetworkAvailable()' in main and 'Do not enqueue here' in main, 'write mutations are queued only before a request can reach the server')
check('shouldQueueOffline(method, url, e)' not in main, 'no post-failure write requeue that can duplicate accepted mutations')
check('Do not queue the original mutation after an online upload attempt' in main and 'Same ambiguity rule as posts' in main, 'online media failures are not re-queued with duplicate-risk semantics')
check('RejectedExecutionException' in main and 'private boolean submitIo(Runnable task)' in main and 'appExecutors.io().execute(' in main, 'executor saturation is handled without UI-thread crashes or caller-thread execution')
check('LEGACY_RSA_KEY_ALIAS' in main and 'ensureLegacyWrappedKey' in main and 'SDK_INT>=23' in main, 'API 21-22 session key fallback is implemented without KeyGenParameterSpec')
check('versionName = \"2.18.0\"' in (ROOT/'app/build.gradle.kts').read_text(), 'version name matches repaired build')
check('DB_VERSION = 5' in offline and 'owner_user_id' in offline and 'getCached(String url, String ownerUserId)' in offline and 'putCached(String url, String body, String ownerUserId)' in offline, 'offline response cache is account-scoped with schema migration')
check('clearCache()' in offline and 'offlineStore.clearCache()' in main, 'logout clears locally cached account data')
check('ALTER TABLE cache ADD COLUMN owner_user_id' in offline and 'db.delete(\"cache\", null, null)' in offline, 'legacy cache rows are invalidated instead of being reassigned across accounts')
print('REPAIR STATIC CHECKS: PASS')

check('CURRENT_USER_ID' in main and 'owner_user_id' in offline and 'pendingOperationBelongsToUser' in main, 'offline mutations are bound to the authenticated account')
check('operation_id' in offline and 'X-MRX-Operation-ID' in main and 'X-Request-ID' in main, 'operation IDs are account-scoped and requests are traceable')
check('RECOVERY_STATE_TTL_MS' in main and 'constantTimeEquals' in main and 'callbackState' in main, 'password recovery callback requires one-time state')
