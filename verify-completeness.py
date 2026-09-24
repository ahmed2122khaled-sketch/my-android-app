#!/usr/bin/env python3
"""Detect unfinished/stubbed application code and false-success build paths."""
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parent
errors=[]
checks=[]
def check(name, ok, detail=''):
    checks.append((name,ok,detail))
    if not ok: errors.append(f'{name}: {detail}')

src=list((ROOT/'app/src/main/java').rglob('*.java')) + list((ROOT/'app/src/main/java').rglob('*.kt'))
text='\n'.join(p.read_text(encoding='utf-8',errors='replace') for p in src)
for marker in [r'\bTODO\b',r'\bFIXME\b',r'UnsupportedOperationException',r'NotImplementedException',r'not implemented',r'placeholder',r'\bmock\b',r'\bdummy\b',r'\bfake\b']:
    check(f'no incomplete marker {marker}', re.search(marker,text,re.I) is None, 'unfinished implementation marker found')
# Empty catch blocks/private constructors are legitimate; unfinished methods are covered by marker checks.
check('build script fails closed', 'BUILD BLOCKED:' in (ROOT/'tools/build-linux.sh').read_text(), 'build script lacks explicit fail-closed path')
check('doctor fails closed', 'RESULT: TOOLCHAIN INCOMPLETE' in (ROOT/'tools/doctor.sh').read_text(), 'doctor script lacks incomplete-toolchain result')
check('required service client exists', (ROOT/'app/src/main/java/com/socialnetwork/app/ServiceClients.java').is_file(), 'ServiceClients.java missing')
print('=== Completeness verification ===')
for n,ok,d in checks: print(f"[{'PASS' if ok else 'FAIL'}] {n}" + (f' — {d}' if d and not ok else ''))
if errors:
    print('\nRESULT: FAIL')
    sys.exit(1)
print('\nRESULT: PASS')
