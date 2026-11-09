"""Inspect both APKs, exact credentials, canonical Profile, and app-only device data/logs.
Never emits credential, ciphertext, raw log messages, or device identity identifiers.
"""
import hashlib,json,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device
key=(Path.home()/'.config/dicta/deepseek_api_key').read_bytes().strip()
assert key
results=[]
for relative in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
    apk=ROOT/relative
    with zipfile.ZipFile(apk) as archive:
        assert all(key not in archive.read(n) for n in archive.namelist()),'Credential found in APK'
        if 'androidTest' not in relative:
            expected=list((ROOT/'profiles/default').glob('*.json'))+[ROOT/'profiles/default/prompt.md']
            for p in expected: assert archive.read('assets/default/'+p.name)==p.read_bytes(),'Canonical profile drift'
            assert all(n.startswith('assets/default/') for n in archive.namelist() if n.startswith('assets/'))
            dex=b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
            assert b'FakeProvider' not in dex and b'SecureProvisionTest' not in dex,'Test code in app APK'
    results.append({'apk':relative,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'credentialAbsent':True})
aapt=Path.home()/'.local/share/dicta-tools/android-sdk/build-tools/35.0.0/aapt'
permissions=subprocess.check_output([aapt,'dump','permissions',ROOT/results[0]['apk']],text=True)
assert 'android.permission.INTERNET' in permissions
assert not any(x in permissions for x in ('READ_CONTACTS','READ_EXTERNAL_STORAGE','SYSTEM_ALERT_WINDOW','BIND_ACCESSIBILITY_SERVICE'))
ciphertext=device.adb('exec-out','run-as','dev.dicta.prototype','cat','no_backup/provider.enc',binary=True)
assert len(ciphertext)>29 and key not in ciphertext,'Unprotected stored credential'
packages=device.shell('cmd','package','list','packages','-U','dev.dicta.prototype').splitlines()
uid=next(row for row in packages if row.startswith('package:dev.dicta.prototype uid:')).split('uid:')[1].split()[0]
logs=device.adb('exec-out','logcat','-d','--uid='+uid,binary=True)
assert key not in logs,'Credential in app logs'
before=json.loads((ROOT/'artifacts/m3/credential-before.json').read_text())
assert hashlib.sha256(ciphertext).hexdigest()==before['sha256'],'Existing credential ciphertext changed'
result={'originalCredentialCiphertextUnchanged':True,'apks':results,'canonicalProfileEqual':True,'testCodeAbsentFromApp':True,'encryptedDeviceCredential':True,'credentialAbsentFromAppLogs':True,'appLogBytesScanned':len(logs),'internetPermission':True,'forbiddenPermissionsAbsent':True}
(ROOT/'docs/m3/evidence/security.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
