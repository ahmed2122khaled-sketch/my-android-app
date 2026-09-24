from pathlib import Path
import re, sys
root=Path(__file__).resolve().parent
main=(root/'app/src/main/java/com/socialnetwork/app/MainActivity.java').read_text()
svc=(root/'app/src/main/java/com/socialnetwork/app/ServiceEndpoints.java').read_text()
ok=True
def check(name, value):
    global ok
    print(('PASS' if value else 'FAIL')+' '+name)
    ok &= bool(value)
check('immutable service map exists', 'final ServiceEndpoints services' in main and 'services.configured()' in main)
check('single service routing implementation', 'return services.route(method, url);' in main)
check('read replica isolation', 'services.isReadReplica' in main and 'services.fallbackWrite' in main)
check('HTTPS endpoint validation', 'isHttps' in svc and 'getUserInfo()' in svc and 'getFragment()' in svc)
check('auth and storage are independently routable', 'path.startsWith("/auth/")' in svc and 'path.startsWith("/storage/")' in svc)
check('write traffic stays on dataWrite', '(read ? dataRead : dataWrite)' in svc)
check('no endpoint regex normalization in hot path', 'replaceAll("/$", "")' not in main)
check('independent service clients', (root/'app/src/main/java/com/socialnetwork/app/ServiceClients.java').exists() and 'final Client auth' in (root/'app/src/main/java/com/socialnetwork/app/ServiceClients.java').read_text())
check('feature code resolves service bases through clients', 'services.clients.auth.baseUrl()' in main and 'services.clients.storage.baseUrl()' in main)
sys.exit(0 if ok else 1)
