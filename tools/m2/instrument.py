"""Run instrumentation without Gradle UTP's package uninstall/cleanup behavior."""
import sys,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device
log=ROOT/'docs/m2/evidence/instrumentation.txt'
args=['adb','-s',device.SERIAL,'shell','am','instrument','-w','-e','notClass','dev.dicta.prototype.SecureProvisionTest','dev.dicta.prototype.test/androidx.test.runner.AndroidJUnitRunner']
with log.open('w') as output:
    result=subprocess.run(args,stdout=output,stderr=subprocess.STDOUT,timeout=600)
text=log.read_text()
if result.returncode or 'FAILURES' in text or 'OK (' not in text: raise SystemExit('Instrumentation failed; inspect sanitized test output')
print(text[-300:])
