"""Inspect deliverable APK without printing credentials or archive content."""
from pathlib import Path
import hashlib,json,zipfile,subprocess
root=Path(__file__).resolve().parents[2]
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
key=(Path.home()/'.config/dicta/deepseek_api_key').read_bytes().strip()
assert key,'Empty credential sentinel'
with zipfile.ZipFile(apk) as z:
    names=z.namelist()
    assert all(key not in z.read(n) for n in names),'Secret detected in APK'
    expected=list((root/'profiles/default').glob('*.json'))+[root/'profiles/default/prompt.md']
    for p in expected:assert z.read('assets/default/'+p.name)==p.read_bytes(),'Profile asset drift'
    for p in (root/'docs/m0/examples').glob('*.json'):assert z.read('assets/examples/'+p.name)==p.read_bytes(),'Fixture drift'
    asset_names=[n for n in names if n.startswith('assets/')]
    assert all(n.startswith(('assets/default/','assets/examples/')) for n in asset_names),'Unexpected assets'
sdk=Path.home()/'.local/share/dicta-tools/android-sdk'
aapt=sdk/'build-tools/35.0.0/aapt'
permissions=subprocess.check_output([aapt,'dump','permissions',apk],text=True)
assert 'android.permission.INTERNET' not in permissions,'Unexpected network permission'
assert not any(x in permissions for x in ('READ_CONTACTS','READ_EXTERNAL_STORAGE','SYSTEM_ALERT_WINDOW','BIND_ACCESSIBILITY_SERVICE'))
result={'apk':str(apk.relative_to(root)),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'secretAbsent':True,'canonicalAssetsEqual':True,'internetPermission':False,'assetCount':len(asset_names)}
(root/'docs/m1/evidence/apk.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
