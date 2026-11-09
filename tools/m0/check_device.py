"""Read-only ADB check. Identity properties are the only reads before validation."""
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

def adb(*args):
    return subprocess.run(['adb',*args],check=True,capture_output=True,text=True,timeout=15).stdout.strip()

rows=[line.split() for line in adb('devices').splitlines()[1:] if line.strip()]
if len(rows)!=1 or rows[0][1]!='device':
    raise SystemExit('Expected exactly one authorized device; no device operations performed.')
serial=rows[0][0]
def shell(*args): return adb('-s',serial,'shell',*args)
props={p:shell('getprop',p) for p in ['ro.product.manufacturer','ro.product.model','ro.product.device','ro.lineage.device']}
if props['ro.product.manufacturer']!='OnePlus' or props['ro.product.model'] not in ['ONEPLUS A6013','ONEPLUS A6010'] or 'fajita' not in (props['ro.product.device'],props['ro.lineage.device']):
    raise SystemExit('Device identity does not match authorized OnePlus 6T/fajita; stopped.')
version=shell('dumpsys','package','com.android.chrome')
match=re.search(r'versionName=([^\s]+)',version)
result={'timeUtc':datetime.now(timezone.utc).isoformat(),'connectedDevices':1,'identity':props,
        'androidRelease':shell('getprop','ro.build.version.release'),'apiLevel':int(shell('getprop','ro.build.version.sdk')),
        'chromeVersion':match.group(1) if match else None,
        'chromeViewHandler':shell('cmd','package','resolve-activity','--brief','-a','android.intent.action.VIEW','-d','https://example.com','-p','com.android.chrome').splitlines()[-1],
        'displaySize':shell('wm','size'),'density':shell('wm','density'),
        'operations':'Identity, package and display queries only; no launch/install/data changes.',
        'popupEndToEnd':'Deferred to M1: no native app exists in M0.'}
path=Path(__file__).resolve().parents[2]/'docs/m0/evidence/adb.json'
path.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
