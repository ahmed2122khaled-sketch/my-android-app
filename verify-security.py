from pathlib import Path
import re

root=Path(__file__).resolve().parent
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
main=(root/'app/src/main/java/com/socialnetwork/app/MainActivity.java').read_text()
net=(root/'app/src/main/res/xml/network_security_config.xml').read_text()
backup=(root/'app/src/main/res/xml/backup_rules.xml').read_text()
extract=(root/'app/src/main/res/xml/data_extraction_rules.xml').read_text()

def check(name, ok): print(('PASS' if ok else 'FAIL')+' '+name); return ok

ok=True
ok &= check('FLAG_SECURE enabled', 'FLAG_SECURE' in main)
ok &= check('HTTPS-only network config', 'cleartextTrafficPermitted="false"' in net)
ok &= check('System CA trust only', '<certificates src="system" />' in net)
ok &= check('Backup disabled', 'android:allowBackup="false"' in manifest)
ok &= check('Backup rules exclude app-private data', '<exclude domain="sharedpref" path="." />' in backup and '<exclude domain="database" path="." />' in backup)
ok &= check('Device/cloud extraction excluded', '<device-transfer>' in extract and '<cloud-backup' in extract)
ok &= check('Request endpoints are pinned to configured service origins', 'isTrustedEndpoint(url)' in main and 'services.trusts' in main and 'dataReadUrl()' in main and 'storageUrl()' in main)
code=re.sub(r'/\*.*?\*/|//[^\n]*', '', main, flags=re.S)
ok &= check('No WebView/browser shell', not re.search(r'\bWebView\b|loadUrl\s*\(|addJavascriptInterface|CustomTabsIntent', code))
ok &= check('No service-role/secret key literal', not re.search(r'service_role|serviceRole|SUPABASE_SERVICE_ROLE|SUPABASE_SECRET', main, re.I))
ok &= check('No cleartext URLs in Java', 'http://' not in main)
print('SECURITY VERIFICATION: '+('PASS' if ok else 'FAIL'))
raise SystemExit(0 if ok else 1)
