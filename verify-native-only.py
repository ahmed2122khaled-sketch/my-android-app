from pathlib import Path
root=Path(__file__).resolve().parent
src=root/'app/src/main'
java='\n'.join(p.read_text(errors='ignore') for p in src.rglob('*.java'))
forbidden=['android.webkit.WebView','loadUrl(','addJavascriptInterface','CustomTabsIntent','androidx.browser','ACTION_VIEW,Uri.parse']
found=[x for x in forbidden if x in java]
assert not found, f'Forbidden browser/web runtime references: {found}'
manifest=(src/'AndroidManifest.xml').read_text()
assert 'android.intent.action.MAIN' in manifest and 'android.intent.category.LAUNCHER' in manifest
assert 'android:usesCleartextTraffic="false"' in manifest
print('Native-only verification: PASS')
print('No WebView/browser application shell detected.')
