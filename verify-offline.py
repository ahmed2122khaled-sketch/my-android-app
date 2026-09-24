#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).parent
main=(root/'app/src/main/java/com/socialnetwork/app/MainActivity.java').read_text()
store=(root/'app/src/main/java/com/socialnetwork/app/OfflineStore.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
checks={
 'offline SQLite store': 'class OfflineStore extends SQLiteOpenHelper' in store,
 'response cache': 'putCached' in store and 'getCached' in store,
 'pending mutation queue': 'enqueue(' in store and 'pendingOperations' in store,
 'network monitor': 'registerDefaultNetworkCallback' in main,
 'offline GET fallback': 'networkAvailableAtStart' in main and 'offlineStore.getCached' in main,
 'automatic sync': 'syncPendingOperations' in main and 'deletePending' in main,
 'offline media persistence': 'offline-media' in main and 'OFFLINE_POST_URL' in main and 'OFFLINE_STORY_URL' in main,
 'network state permission': 'android.permission.ACCESS_NETWORK_STATE' in manifest,
 'keystore tokens unchanged': 'saveSecret(ACCESS_TOKEN' in main and 'saveSecret(REFRESH_TOKEN' in main,
 'offline mutations are bound to account identity': 'owner_user_id' in store and 'CURRENT_USER_ID' in main and 'pendingOperationBelongsToUser' in main,
 'pending queue is account-scoped during reads': 'pendingOperations(int limit, String ownerUserId)' in store and 'pendingOperations(50, ownerUserId)' in main,
 'retry scheduling is account-scoped': 'hasRetryablePending(int maxAttempts, String ownerUserId)' in store and 'hasRetryablePending(MAX_OFFLINE_SYNC_ATTEMPTS, activeOwner)' in main,
}
for k,v in checks.items(): print(f"[{'PASS' if v else 'FAIL'}] {k}")
raise SystemExit(0 if all(checks.values()) else 1)
