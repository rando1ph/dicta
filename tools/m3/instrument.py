"""Run proportional M3 tests without UTP uninstalling the app or clearing user data."""
import sys,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device
classes=sys.argv[1:] or ['M3StorageTest','M3ProfileDeviceTest','M3UiTest','LargeFontPopupTest','PlatformTest']
log=ROOT/'docs/m3/evidence'/('instrumentation-'+('-'.join(c.replace('#','-') for c in classes))+'.txt')
args=['adb','-s',device.SERIAL,'shell','am','instrument','-w','-e','class',','.join('dev.dicta.prototype.'+c for c in classes),'dev.dicta.prototype.test/androidx.test.runner.AndroidJUnitRunner']
with log.open('w') as output:
    result=subprocess.run(args,stdout=output,stderr=subprocess.STDOUT,timeout=600)
text=log.read_text()
print(text[-6500:])
if result.returncode or 'FAILURES' in text or 'OK (' not in text:raise SystemExit('Instrumentation failed; see evidence log')
