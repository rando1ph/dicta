"""Observed ADB UI actions on the identity-guarded OnePlus 6T. No data reset."""
import sys,json,time,re,shlex
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device,smoke
ART=ROOT/'artifacts/m4'
ART.mkdir(exist_ok=True)
def capture(name):
    foreground=smoke.foreground()
    assert foreground.startswith(('dev.dicta.prototype/','com.android.chrome/'))
    if foreground.startswith('dev.dicta.prototype/'):
        assert not smoke.visible('API Key'),'Never capture credentials settings'
    path=ART/(name+'.png');path.write_bytes(device.adb('exec-out','screencap','-p',binary=True));print(path)
if __name__=='__main__':
    action=sys.argv[1]
    if action=='texts':print(json.dumps(smoke.texts(),ensure_ascii=False,indent=2))
    elif action=='capture':capture(sys.argv[2])
    elif action=='tap':smoke.tap(text=sys.argv[2])
    elif action=='desc':smoke.tap(desc=sys.argv[2])
    elif action=='open':print(device.shell('am','start','-a','android.intent.action.VIEW','-d',shlex.quote(sys.argv[2]),'-p','com.android.chrome'))
    elif action=='swipe':device.shell('input','swipe',*sys.argv[2:])
    elif action=='key':device.shell('input','keyevent',sys.argv[2])
    elif action=='xy':device.shell('input','tap',*sys.argv[2:])
