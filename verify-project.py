#!/usr/bin/env python3
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET, zipfile

ROOT = Path(__file__).resolve().parents[1]

def check(ok, msg):
    print(('PASS: ' if ok else 'FAIL: ') + msg)
    if not ok: sys.exit(1)

main = (ROOT/'app/src/main/java/com/socialnetwork/app/MainActivity.java').read_text()
endpoints = (ROOT/'app/src/main/java/com/socialnetwork/app/ServiceEndpoints.java').read_text()
offline = (ROOT/'app/src/main/java/com/socialnetwork/app/OfflineStore.java').read_text()
gradle = (ROOT/'app/build.gradle.kts').read_text()
manifest = ROOT/'app/src/main/AndroidManifest.xml'

# Java/XML structural gate.
java_files = list((ROOT/'app/src/main/java').rglob('*.java'))
for p in java_files:
    s = p.read_text()
    check(s.count('{') == s.count('}'), f'{p.relative_to(ROOT)} brace balance')
    check(s.count('(') == s.count(')'), f'{p.relative_to(ROOT)} parenthesis balance')
    check(s.count('[') == s.count(']'), f'{p.relative_to(ROOT)} bracket balance')
for p in (ROOT/'app/src/main/res').rglob('*.xml'):
    ET.parse(p)
check(True, 'all resource XML parses')
ET.parse(manifest)
check(True, 'AndroidManifest parses')

# Native-only/security invariants.
all_java='\n'.join(p.read_text() for p in java_files)
check('android.webkit.WebView' not in all_java and 'loadUrl(' not in all_java, 'no WebView/browser shell')
check('http://' not in all_java, 'no cleartext HTTP literal in Java sources')
check('FLAG_SECURE' in main, 'screen capture protection remains enabled')
check('RejectedExecutionException' in main and 'private boolean submitIo(Runnable task)' in main, 'executor saturation is surfaced without UI-thread execution')
check(main.count('onNewIntent(Intent intent)') == 1, 'single onNewIntent implementation')
check('networkAvailableAtStart = isNetworkAvailable()' in main, 'offline decision is made before mutation request')
check('Do not enqueue here' in main, 'post-failure mutation is not blindly re-queued')
check('Never evict' in offline and 'MAX_PENDING_ENTRIES' in offline, 'offline queue protects existing user mutations')
check('pathFromOrigin(url, primary)' in endpoints and 'startsWith(primary)' not in endpoints, 'service routing uses origin-aware matching')
check('versionCode = 44' in gradle and 'versionName = "2.18.0"' in gradle, 'version metadata matches repair release')
check('RECOVERY_STATE_TTL_MS' in main and 'constantTimeEquals' in main and 'clearRecoveryState()' in main, 'password recovery callback is state-bound and one-time')
check('setInstanceFollowRedirects(false)' in main, 'HTTP requests never follow redirects automatically')
check('NETWORK_CONNECT_TIMEOUT_MS' in main and 'NETWORK_READ_TIMEOUT_MS' in main, 'network timeout constants are used by authenticated requests')
check('syncInFlight = false;\n        }' in main and 'syncHandler.postAtTime(this::syncPendingOperations, "offline-sync"' in main, 'offline sync rejection and retry scheduling are lifecycle-safe')
check('profileUpdateInFlight=false;\n        }' in main and 'peopleLoadInFlight = false;\n        }' in main, 'executor rejection cannot permanently lock profile/people operations')
check('notificationsMarkReadInFlight=false;\n        }' in main and 'messageSendInFlight=false;\n            field.setEnabled(true);' in main, 'executor rejection cannot permanently lock notification/message operations')
check('storyInFlight=false;\n        }' in main and 'publishInFlight=false;\n            publishButton.setEnabled(true);' in main, 'executor rejection cannot permanently lock media publishing')
check('versionCode = 44' in gradle and 'versionName = \"2.18.0\"' in gradle, 'free-policy release metadata is current')
print('PROJECT STATIC GATE: PASS')
