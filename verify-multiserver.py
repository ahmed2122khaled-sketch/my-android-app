from pathlib import Path
import re, sys
root=Path(__file__).parent
main=(root/'app/src/main/java/com/socialnetwork/app/MainActivity.java').read_text()
endpoints=(root/'app/src/main/java/com/socialnetwork/app/ServiceEndpoints.java').read_text()
gradle=(root/'app/build.gradle.kts').read_text()
props=(root/'supabase.properties.example').read_text()
ok=True

def check(name, cond):
    global ok
    if cond: print('[PASS]',name)
    else: print('[FAIL]',name); ok=False

for key in ['SUPABASE_DATA_READ_URL','SUPABASE_DATA_WRITE_URL','SUPABASE_AUTH_URL','SUPABASE_STORAGE_URL']:
    check(key+' build config', key in gradle)
    check(key+' example config', key in props)
check('central request routing', 'routeServiceEndpoint(method, url)' in main)
check('canonical endpoint trust before routing', 'isTrustedEndpoint(url)' in main)
check('exact-origin routing', 'pathFromOrigin' in endpoints and 'sameOrigin(u, b)' in endpoints)
check('routed endpoint trust after routing', 'isTrustedEndpoint(routedUrl)' in main)
check('GET/HEAD read routing', 'GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)' in main)
check('REST mutations use write routing', '(read ? dataRead : dataWrite) + path' in endpoints)
check('Auth routing', 'path.startsWith("/auth/")' in endpoints and 'auth + path' in endpoints)
check('Storage routing', 'path.startsWith("/storage/")' in endpoints and 'storage + path' in endpoints)
check('Storage upload uses storage origin', 'new URL(storageUrl()+"/storage/v1/object/media/"+path)' in main)
check('Storage public media uses storage origin', 'storageUrl()+"/storage/v1/object/public/media/"+path' in main)
check('No undefined media viewer routing variables', 'routeServiceEndpoint(method, url)' not in main.split('showNativeMediaViewer',1)[1].split('private',1)[0])
check('Read replica fallback helper', 'readFallbackUrl(url)' in main and 'requestDirect(method,readFallbackUrl(url),body,token)' in main)
check('Read replica transport fallback', 'isReadReplicaRequest(method,url)' in main)
check('Storage-domain media trust', 'Uri base=Uri.parse(storageUrl())' in main)
check('Message duplicate-send guard', 'messageSendInFlight' in main and 'if(messageSendInFlight) return' in main)
check('Profile duplicate-update guard', 'profileUpdateInFlight' in main and 'if(profileUpdateInFlight) return' in main)
check('People search normalization', 'normalizeUsernameSearch(search)' in main)
check('No direct primary REST HttpURLConnection', not re.search(r'new URL\(supabaseUrl\(\)\+"/rest/', main))
print('MULTI-SERVER VERIFICATION:', 'PASS' if ok else 'FAIL')
sys.exit(0 if ok else 1)
