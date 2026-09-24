#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).resolve().parent
main=(root/'app/src/main/java/com/socialnetwork/app/MainActivity.java').read_text(encoding='utf-8')
manifest=(root/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
net=(root/'app/src/main/res/xml/network_security_config.xml').read_text(encoding='utf-8')
checks={
 'video cross-domain redirect disabled': 'android-allow-cross-domain-redirect' in main and '"0"' in main,
 'camera grants explicit': 'FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION' in main,
 'lifecycle-safe toast': 'private void toast(String t)' in main and 'isDestroyed()' in main,
 'network config referenced': '@xml/network_security_config' in manifest,
 'network config denies cleartext': 'cleartextTrafficPermitted="false"' in net,
 'system trust anchors only': '<certificates src="system" />' in net,
 'no WebView shell': all(x not in main for x in ('android.webkit.WebView','loadUrl(','addJavascriptInterface','CustomTabsIntent')),
 'single onDestroy': main.count('protected void onDestroy') == 1,
 'balanced braces': main.count('{') == main.count('}'),
 'balanced parentheses': main.count('(') == main.count(')'),
 'follow target concurrency guard': 'followTargetsInFlight' in main and 'followTargetsInFlight.add(target)' in main,
 'notification read batching': 'markNotificationsRead' in main and 'id=in.(' in main,
 'notification read no per-row PATCH': 'markNotificationRead(n.optString' not in main,
}
for k,v in checks.items(): print(f"[{'PASS' if v else 'FAIL'}] {k}")
if not all(checks.values()): raise SystemExit(1)
print('HARDENING VERIFICATION: PASS')
