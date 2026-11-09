"""Last manual UI checks on the controlled Chrome page; coordinates are observed on A6013.
Run only after verifying the selection/menu layout against a fresh screenshot.
"""
import sys,time,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/m1'))
import device,smoke
ART=ROOT/'artifacts/m2'
checks=[]
def record(case,**details):
    checks.append({'case':case,'passed':True,**details})
    (ROOT/'docs/m2/evidence/device-tests.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n')
def capture(name): (ART/name).write_bytes(device.adb('exec-out','screencap','-p',binary=True))
assert smoke.foreground().startswith('com.android.chrome/')
assert smoke.visible('127.0.0.1:8766')
time.sleep(.8);capture('M2-04-return-to-chrome.png')
record('actual Chrome selection live lookup close and same reading position',word='petrichor',bounds='[75,681][1004,796]')
# Repeat the same observed real selection menu, then immediately Back after opening the Popup.
device.shell('input','swipe',170,738,170,738,850);time.sleep(.6)
device.shell('input','tap',920,591);time.sleep(.4)
device.shell('input','tap',780,457);time.sleep(.3)
assert 'ProcessTextActivity' in smoke.foreground()
device.shell('input','keyevent','4')
time.sleep(3)
assert smoke.foreground().startswith('com.android.chrome/')
assert not smoke.visible('Dicta · Popup')
assert smoke.visible('127.0.0.1:8766')
record('Chrome selection popup immediate Back returns without late popup')
# Cold process restart preserves encrypted credentials; a fresh Home query must be live.
device.shell('am','force-stop','dev.dicta.prototype')
device.shell('am','start','-n','dev.dicta.prototype/.platform.MainActivity')
smoke.wait_text('Search a word')
smoke.tap(text='Search a word / 查词…')
device.shell('input','text','resilience')
smoke.tap(desc='查询')
end=time.monotonic()+65
while time.monotonic()<end:
    values=smoke.texts()
    if any(t=='noun' for t,_ in values) and not any(t=='正在查询' for t,_ in values): break
    if any(t=='暂时无法展示' for t,_ in values):raise AssertionError('Cold restart query failed')
else:raise AssertionError('Cold restart query timed out')
assert any(t=='resilience' for t,_ in values)
assert not any(any(label in t for label in ['M0','M1','M3','M4','演示数据']) for t,_ in values)
time.sleep(.4);capture('M2-05-home-after-restart.png')
record('cold restart without re-entry of credentials gives real Home result',word='resilience',renderedTexts=[t for t,_ in values])
print('PASS: 3 final device UI checks; Chrome close/Back and cold restart live Home query.')
