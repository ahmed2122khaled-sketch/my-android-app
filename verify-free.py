#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[1]
def check(ok,msg):
    print(("PASS: " if ok else "FAIL: ")+msg)
    if not ok: sys.exit(1)
gradle=(ROOT/"app/build.gradle.kts").read_text()
manifest=(ROOT/"app/src/main/AndroidManifest.xml").read_text()
java="\n".join(p.read_text(errors="ignore") for p in (ROOT/"app/src/main/java").rglob("*.java"))
xml="\n".join(p.read_text(errors="ignore") for p in (ROOT/"app/src/main/res").rglob("*.xml"))
allsrc=gradle+"\n"+manifest+"\n"+java+"\n"+xml
check("com.android.billingclient" not in gradle and "play:billing" not in gradle,"no Google Play Billing dependency")
check("com.android.vending.BILLING" not in manifest,"no Android billing permission")
for pattern,label in [(r"(?i)\b(premium|paid[_-]?(?:only|feature|access)|trial|paywall|in[_-]?app[_-]?purchase|purchase[_-]?required|payment[_-]?required|subscription[_-]?required)\b","paid feature/purchase gate"),(r"(?i)\b(billingclient|billing_client)\b","billing implementation")]:
    check(not re.search(pattern,allsrc),"no "+label)
check((ROOT/"FREE_POLICY.md").exists(),"free policy document exists")
print("FREE POLICY GATE: PASS")
