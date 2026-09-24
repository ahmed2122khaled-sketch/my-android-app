#!/usr/bin/env python3
from pathlib import Path
import re
root = Path(__file__).resolve().parent
main = (root/'app/src/main/java/com/socialnetwork/app/MainActivity.java').read_text(encoding='utf-8')
gradle = (root/'app/build.gradle.kts').read_text(encoding='utf-8')

checks = {
    'lifecycle postUi re-checks before execution': 'main.post(()->{' in main and 'if(isFinishing() || isDestroyed()) return;' in main,
    'destroy removes pending UI callbacks': 'main.removeCallbacksAndMessages(null);' in main,
    'finishing activity cleans pending camera URI': 'if (isFinishing() && pendingCameraUri != null)' in main,
    'document picker requests persistable read grant': main.count('FLAG_GRANT_PERSISTABLE_URI_PERMISSION') >= 2,
    'camera intent carries ClipData URI grant': 'ClipData.newRawUri("output", pendingCameraUri)' in main,
    'MIME-to-extension mapping is explicit': 'private String mediaExtension(String mime)' in main and 'image/gif' in main and 'video/webm' in main,
    'unsupported MIME is rejected': 'نوع الوسائط غير مدعوم' in main,
    'offline queue is user-bound': 'CURRENT_USER_ID' in main and 'ownerUserId' in main and 'pendingOperationBelongsToUser' in main,
    'legacy plaintext user-id cache removed': 'prefs.getString(\"offline_user_id\"' not in main and 'prefs.edit().putString(\"offline_user_id\"' not in main,
    'request body limit exists': 'MAX_REQUEST_BODY_BYTES' in main and 'payload.length>MAX_REQUEST_BODY_BYTES' in main,
    'partial upload cleanup exists': 'cleanupUploadedPath(path, requestRefreshToken)' in main,
    'cleanup retries after refresh': 'cleanupUploadedPath(String path, String failedRefreshToken)' in main and 'refreshSessionIfNeeded(failedRefreshToken)' in main,
    'media URL port is constrained': all(x in main for x in ['uri.getPort()==-1 ? 443 : uri.getPort()', 'base.getPort()==-1 ? 443 : base.getPort()', '!path.startsWith("/storage/v1/object/public/media/")']),
    'version bumped after hardening': (lambda m: bool(m and int(m.group(1)) >= 40))(re.search(r'versionCode\s*=\s*(\d+)', gradle)),
    'no WebView shell': not re.search(r'WebView|loadUrl\(|addJavascriptInterface|CustomTabsIntent|androidx\.browser', re.sub(r'/\*.*?\*/|//[^\n]*','',main,flags=re.S)),
    'no cleartext URL in Java': 'http://' not in main,
}
for k,v in checks.items(): print(f"[{'PASS' if v else 'FAIL'}] {k}")
if not all(checks.values()): raise SystemExit(1)
print('REGRESSION/HARDENING VERIFICATION: PASS')

