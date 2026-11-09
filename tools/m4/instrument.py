"""Guarded targeted instrumentation; never invokes UTP uninstall/clear-data."""
import sys,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device
classes=sys.argv[1:] or ['M4SpeechTest']
log=ROOT/'docs/m4/evidence'/('instrumentation-'+('-'.join(c.replace('#','-') for c in classes))+'.txt')
args=['adb','-s',device.SERIAL,'shell','am','instrument','-w','-e','class',','.join('dev.dicta.prototype.'+c for c in classes),'dev.dicta.prototype.test/androidx.test.runner.AndroidJUnitRunner']
with log.open('w') as output:
    result=subprocess.run(args,stdout=output,stderr=subprocess.STDOUT,timeout=500)
text=log.read_text();print(text[-5500:])
if result.returncode or 'FAILURES' in text or 'OK (' not in text:raise SystemExit('Instrumentation failed; see evidence log')
